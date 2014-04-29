package org.scoringengine.pfa

import scala.collection.mutable
import scala.language.postfixOps
import scala.runtime.ScalaRunTime

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.node.BooleanNode
import org.codehaus.jackson.node.DoubleNode
import org.codehaus.jackson.node.IntNode
import org.codehaus.jackson.node.JsonNodeFactory
import org.codehaus.jackson.node.LongNode
import org.codehaus.jackson.node.NullNode
import org.codehaus.jackson.node.TextNode
import org.codehaus.jackson.node.BinaryNode

import org.scoringengine.pfa.errors.PFASyntaxException
import org.scoringengine.pfa.errors.PFASemanticException
import org.scoringengine.pfa.jvmcompiler.JavaCode
import org.scoringengine.pfa.jvmcompiler.JVMCompiler
import org.scoringengine.pfa.jvmcompiler.JVMNameMangle
import org.scoringengine.pfa.util.convertFromJson
import org.scoringengine.pfa.util.convertToJson

import org.scoringengine.pfa.signature.IncompatibleTypes
import org.scoringengine.pfa.signature.LabelData
import org.scoringengine.pfa.signature.P
import org.scoringengine.pfa.signature.Sig
import org.scoringengine.pfa.signature.Signature
import org.scoringengine.pfa.signature.Sigs

import org.scoringengine.pfa.types.Type
import org.scoringengine.pfa.types.FcnType
import org.scoringengine.pfa.types.AvroConversions._
import org.scoringengine.pfa.types.AvroType
import org.scoringengine.pfa.types.AvroCompiled
import org.scoringengine.pfa.types.AvroNumber
import org.scoringengine.pfa.types.AvroRaw
import org.scoringengine.pfa.types.AvroIdentifier
import org.scoringengine.pfa.types.AvroContainer
import org.scoringengine.pfa.types.AvroMapping
import org.scoringengine.pfa.types.AvroNull
import org.scoringengine.pfa.types.AvroBoolean
import org.scoringengine.pfa.types.AvroInt
import org.scoringengine.pfa.types.AvroLong
import org.scoringengine.pfa.types.AvroFloat
import org.scoringengine.pfa.types.AvroDouble
import org.scoringengine.pfa.types.AvroBytes
import org.scoringengine.pfa.types.AvroFixed
import org.scoringengine.pfa.types.AvroString
import org.scoringengine.pfa.types.AvroEnum
import org.scoringengine.pfa.types.AvroArray
import org.scoringengine.pfa.types.AvroMap
import org.scoringengine.pfa.types.AvroRecord
import org.scoringengine.pfa.types.AvroField
import org.scoringengine.pfa.types.AvroUnion
import org.scoringengine.pfa.types.AvroPlaceholder
import org.scoringengine.pfa.types.AvroTypeBuilder

package object ast {
  def inferType(
    expr: Expression,
    symbols: Map[String, AvroType] = Map[String, AvroType](),
    cells: Map[String, Cell] = Map[String, Cell](),
    pools: Map[String, Pool] = Map[String, Pool](),
    fcns: Map[String, UserFcn] = Map[String, UserFcn]()): AvroType = {

    val symbolTable = SymbolTable(None, mutable.Map[String, AvroType](symbols.toSeq: _*), cells, pools, true, false)
    val functionTable = FunctionTable(FunctionTable.blank.functions ++ fcns)

    val (context, result) = expr.walk(NoTask, symbolTable, functionTable)
    context.asInstanceOf[ExpressionContext].retType
  }
}

package ast {
  //////////////////////////////////////////////////////////// symbols

  object validSymbolName extends Function1[String, Boolean] {
    val pattern = "[A-Za-z_][A-Za-z0-9_]*".r
    def apply(test: String): Boolean =
      pattern.unapplySeq(test) != None
  }

  case class SymbolTable(parent: Option[SymbolTable], symbols: mutable.Map[String, AvroType], cells: Map[String, Cell], pools: Map[String, Pool], sealedAbove: Boolean, sealedWithin: Boolean) {
    def getLocal(name: String): Option[AvroType] = symbols.get(name)
    def getAbove(name: String): Option[AvroType] = parent match {
      case Some(p) => p.get(name)
      case None => None
    }
    def get(name: String): Option[AvroType] = getLocal(name) orElse getAbove(name)
    def apply(name: String): AvroType = get(name).get

    def writable(name: String): Boolean =
      if (sealedWithin)
        false
      else
        symbols.get(name) match {
          case Some(x) => true
          case None =>
            if (sealedAbove)
              false
            else
              parent match {
                case Some(p) => p.writable(name)
                case None => throw new IllegalArgumentException("no such symbol: " + name)
              }
        }

    def put(name: String, avroType: AvroType): Unit = symbols.put(name, avroType)

    def cell(name: String): Option[Cell] = cells.get(name) orElse (parent match {
      case Some(p) => p.cell(name)
      case None => None
    })

    def pool(name: String): Option[Pool] = pools.get(name) orElse (parent match {
      case Some(p) => p.pool(name)
      case None => None
    })

    def newScope(sealedAbove: Boolean, sealedWithin: Boolean): SymbolTable =
      SymbolTable(Some(this), mutable.Map[String, AvroType](), Map[String, Cell](), Map[String, Pool](), sealedAbove, sealedWithin)

    def inThisScope: Map[String, AvroType] = symbols.toMap
    def allInScope: Map[String, AvroType] = parent match {
      case Some(p) => p.allInScope ++ symbols.toMap
      case None => symbols.toMap
    }
  }
  object SymbolTable {
    def blank = new SymbolTable(None, mutable.Map[String, AvroType](), Map[String, Cell](), Map[String, Pool](), true, false)
  }

  //////////////////////////////////////////////////////////// functions

  object validFunctionName extends Function1[String, Boolean] {
    val pattern = "[A-Za-z_]([A-Za-z0-9_]|\\.[A-Za-z][A-Za-z0-9_]*)*".r
    def apply(test: String): Boolean =
      pattern.unapplySeq(test) != None
  }

  trait Fcn {
    def sig: Signature
    def javaRef(fcnType: FcnType): JavaCode
    def javaCode(args: Seq[JavaCode], argContext: Seq[AstContext], retType: AvroType): JavaCode
  }

  trait LibFcn extends Fcn {
    def name: String
    def doc: scala.xml.Elem
    def javaRef(fcnType: FcnType): JavaCode = JavaCode(this.getClass.getName + ".MODULE$")
    def javaCode(args: Seq[JavaCode], argContext: Seq[AstContext], retType: AvroType): JavaCode =
      JavaCode("%s.apply(%s)", javaRef(FcnType(argContext collect {case x: ExpressionContext => x.retType}, retType)).toString, args.mkString(", "))
  }

  case class UserFcn(name: String, sig: Sig) extends Fcn {
    import JVMNameMangle._
    def javaRef(fcnType: FcnType) = JavaCode("(new %s())", f(name))
    def javaCode(args: Seq[JavaCode], argContext: Seq[AstContext], retType: AvroType): JavaCode =
      JavaCode("""(new %s()).apply(%s)""", f(name), args.mkString(", "))
  }
  object UserFcn {
    def fromFcnDef(n: String, fcnDef: FcnDef): UserFcn =
      UserFcn(n, Sig(fcnDef.params.map({case (k, t) => (k, P.fromType(t))}), P.fromType(fcnDef.ret)))
  }

  case class EmitFcn(outputType: AvroType) extends Fcn {
    val sig = Sig(List(("output", P.fromType(outputType))), P.Null)
    def javaRef(fcnType: FcnType): JavaCode = throw new PFASemanticException("cannot reference the emit function", None)
    def javaCode(args: Seq[JavaCode], argContext: Seq[AstContext], retType: AvroType): JavaCode =
      JavaCode("W.n(f_emit.apply(%s))", args(0).toString)
  }

  case class FunctionTable(functions: Map[String, Fcn])
  object FunctionTable {
    def blank = new FunctionTable(
      lib1.array.provides ++
      lib1.bytes.provides ++
      lib1.core.provides ++
      lib1.enum.provides ++
      lib1.fixed.provides ++
      lib1.impute.provides ++
      lib1.map.provides ++
      lib1.math.provides ++
      lib1.record.provides ++
      lib1.string.provides ++
      lib1.stat.sample.provides ++
      lib1.model.tree.provides
    )  // TODO: ++ lib1.other.provides ++ lib1.stillother.provides
  }

  //////////////////////////////////////////////////////////// type-checking and transforming ASTs

  trait AstContext
  trait ExpressionContext extends AstContext {
    val retType: AvroType
    val calls: Set[String]
  }
  trait TaskResult

  trait Task extends Function2[AstContext, Option[Type], TaskResult] {
    def apply(astContext: AstContext, resolvedType: Option[Type] = None): TaskResult
  }

  object NoTask extends Task {
    case object EmptyResult extends TaskResult
    def apply(astContext: AstContext, resolvedType: Option[Type] = None): TaskResult = {EmptyResult}
  }

  //////////////////////////////////////////////////////////// AST nodes

  trait Ast {
    def pos: Option[String]
    override def equals(other: Any): Boolean
    override def hashCode(): Int
    def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      if (pf.isDefinedAt(this)) List(pf.apply(this)) else List[X]()

    def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult)
    def walk(task: Task): TaskResult = walk(task, SymbolTable.blank, FunctionTable.blank)._2
    def jsonNode: JsonNode

    override def toString(): String = convertToJson(jsonNode)
  }

  object Method extends Enumeration {
    type Method = Value
    val MAP, EMIT, FOLD = Value
  }

  case class EngineConfig(
    name: String,
    method: Method.Method,
    inputPlaceholder: AvroPlaceholder,
    outputPlaceholder: AvroPlaceholder,
    begin: Seq[Expression],
    action: Seq[Expression],
    end: Seq[Expression],
    fcns: Map[String, FcnDef],
    zero: Option[String],
    cells: Map[String, Cell],
    pools: Map[String, Pool],
    randseed: Option[Long],
    doc: Option[String],
    metadata: Option[JsonNode],
    options: Map[String, JsonNode],
    pos: Option[String] = None) extends Ast {

    def input: AvroType = inputPlaceholder.avroType
    def output: AvroType = outputPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: EngineConfig =>
        this.name == that.name  &&  this.method == that.method  &&  this.inputPlaceholder.toString == that.inputPlaceholder.toString  &&  this.outputPlaceholder.toString == that.outputPlaceholder.toString  &&
        this.begin == that.begin  &&  this.action == that.action  &&  this.end == that.end  &&  this.fcns == that.fcns  &&
        this.cells == that.cells  &&  this.pools == that.pools  &&  this.randseed == that.randseed  &&
        this.doc == that.doc  &&  this.metadata == that.metadata  &&  this.options == that.options  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((name, method, inputPlaceholder.toString, outputPlaceholder.toString, begin, action, end, fcns, cells, pools, randseed, doc, metadata, options))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        begin.flatMap(_.collect(pf)) ++
        action.flatMap(_.collect(pf)) ++
        end.flatMap(_.collect(pf)) ++
        fcns.values.flatMap(_.collect(pf)) ++
        cells.values.flatMap(_.collect(pf)) ++
        pools.values.flatMap(_.collect(pf))

    if (action.size < 1)
      throw new PFASyntaxException("\"action\" must contain least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val topWrapper = SymbolTable(Some(symbolTable), mutable.Map[String, AvroType](), cells, pools, true, false)

      val userFunctions = mutable.Map[String, Fcn]()
      for ((fname, fcnDef) <- fcns) {
        val ufname = "u." + fname
        if (!validFunctionName(ufname))
          throw new PFASemanticException("\"%s\" is not a valid function name".format(fname), pos)
        userFunctions.put(ufname, UserFcn.fromFcnDef(ufname, fcnDef))
      }

      val emitFcn =
        if (method == Method.EMIT)
          Map("emit" -> EmitFcn(output))
        else
          Map[String, Fcn]()

      val withUserFunctions = FunctionTable(functionTable.functions ++ userFunctions.toMap ++ emitFcn)

      val userFcnContexts =
        for ((fname, fcnDef) <- fcns) yield {
          val ufname = "u." + fname
          val scope = topWrapper.newScope(true, false)
          val (fcnContext: FcnDef.Context, _) = fcnDef.walk(task, scope, withUserFunctions)
          (ufname, fcnContext)
        }

      val beginScope = topWrapper.newScope(true, false)
      val beginContextResults: Seq[(ExpressionContext, TaskResult)] =
        begin.map(_.walk(task, beginScope, withUserFunctions)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val beginResults = beginContextResults map {_._2}
      val beginCalls = beginContextResults.map(_._1.calls).flatten.toSet

      val endScope = topWrapper.newScope(true, false)
      val endContextResults: Seq[(ExpressionContext, TaskResult)] =
        end.map(_.walk(task, endScope, withUserFunctions)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val endResults = endContextResults map {_._2}
      val endCalls = endContextResults.map(_._1.calls).flatten.toSet

      // it is important for action to be checked after all functions, begin, and end: see comment below
      val actionScopeWrapper = topWrapper.newScope(true, false)
      actionScopeWrapper.put("input", input)
      if (method == Method.FOLD)
        topWrapper.put("tally", output)  // note that this is after all user functions are defined, which keeps "tally" out of the user functions' scopes
      val actionScope = actionScopeWrapper.newScope(true, false)

      val actionContextResults: Seq[(ExpressionContext, TaskResult)] = action.map(_.walk(task, actionScope, withUserFunctions)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val actionCalls = actionContextResults.map(_._1.calls).flatten.toSet

      if (method == Method.MAP  ||  method == Method.FOLD) {
        if (!output.accepts(actionContextResults.last._1.retType))
          throw new PFASemanticException("action's inferred output type is %s but the declared output type is %s".format(actionContextResults.last._1.retType, output), pos)
      }

      val context = EngineConfig.Context(
        name,
        method,
        input,
        output,
        inputPlaceholder.parser.compiledTypes,
        beginResults,
        beginScope.inThisScope,
        beginCalls,
        actionContextResults map {_._2},
        actionScopeWrapper.inThisScope ++ actionScope.inThisScope,
        actionCalls,
        endResults,
        endScope.inThisScope,
        endCalls,
        userFcnContexts,
        zero,
        cells,
        pools,
        randseed,
        doc,
        metadata,
        options)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("name", name)

      out.put("method", method match {
        case Method.MAP => "map"
        case Method.EMIT => "emit"
        case Method.FOLD => "fold"
      })

      out.put("input", convertFromJson(inputPlaceholder.toString))
      out.put("output", convertFromJson(outputPlaceholder.toString))

      if (!begin.isEmpty) {
        val jsonBegin = factory.arrayNode
        for (expr <- begin)
          jsonBegin.add(expr.jsonNode)
        out.put("begin", jsonBegin)
      }

      val jsonAction = factory.arrayNode
      for (expr <- action)
        jsonAction.add(expr.jsonNode)
      out.put("action", jsonAction)

      if (!end.isEmpty) {
        val jsonEnd = factory.arrayNode
        for (expr <- end)
          jsonEnd.add(expr.jsonNode)
        out.put("end", jsonEnd)
      }

      if (!fcns.isEmpty) {
        val jsonFcns = factory.objectNode
        for ((n, fcnDef) <- fcns)
          jsonFcns.put(n, fcnDef.jsonNode)
        out.put("fcns", jsonFcns)
      }

      zero foreach {x => out.put("zero", convertFromJson(x))}

      val jsonCells = factory.objectNode
      for ((name, cell) <- cells)
        jsonCells.put(name, cell.jsonNode)
      out.put("cells", jsonCells)

      val jsonPools = factory.objectNode
      for ((name, pool) <- pools)
        jsonPools.put(name, pool.jsonNode)
      out.put("pools", jsonPools)

      randseed foreach {x => out.put("randseed", x)}
      doc foreach {x => out.put("doc", x)}
      metadata foreach {x => out.put("metadata", x)}

      val jsonOptions = factory.objectNode
      for ((name, value) <- options)
        jsonOptions.put(name, value)
      out.put("options", jsonOptions)

      out
    }
  }
  object EngineConfig {
    case class Context(
      name: String,
      method: Method.Method,
      input: AvroType,
      output: AvroType,
      compiledTypes: Set[AvroCompiled],
      begin: Seq[TaskResult],
      beginSymbols: Map[String, AvroType],
      beginCalls: Set[String],
      action: Seq[TaskResult],
      actionSymbols: Map[String, AvroType],
      actionCalls: Set[String],
      end: Seq[TaskResult],
      endSymbols: Map[String, AvroType],
      endCalls: Set[String],
      fcns: Map[String, FcnDef.Context],
      zero: Option[String],
      cells: Map[String, Cell],
      pools: Map[String, Pool],
      randseed: Option[Long],
      doc: Option[String],
      metadata: Option[JsonNode],
      options: Map[String, JsonNode]
    ) extends AstContext
  }

  case class Cell(avroPlaceholder: AvroPlaceholder, init: String, shared: Boolean, rollback: Boolean, pos: Option[String] = None) extends Ast {
    def avroType: AvroType = avroPlaceholder.avroType

    if (shared && rollback)
      throw new PFASyntaxException("shared and rollback are mutually incompatible flags for a Cell", pos)

    override def equals(other: Any): Boolean = other match {
      case that: Cell =>
        this.avroPlaceholder.toString == that.avroPlaceholder.toString  &&  convertFromJson(this.init) == convertFromJson(that.init)  &&  this.shared == that.shared  &&  this.rollback == that.rollback  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((avroPlaceholder.toString, convertFromJson(this.init), shared, rollback))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = Cell.Context()
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("type", convertFromJson(avroPlaceholder.toString))
      out.put("init", convertFromJson(init))
      out.put("shared", shared)
      out.put("rollback", rollback)
      out
    }
  }
  object Cell {
    case class Context() extends AstContext
  }

  case class Pool(avroPlaceholder: AvroPlaceholder, init: Map[String, String], shared: Boolean, rollback: Boolean, pos: Option[String] = None) extends Ast {
    def avroType: AvroType = avroPlaceholder.avroType

    if (shared && rollback)
      throw new PFASyntaxException("shared and rollback are mutually incompatible flags for a Pool", pos)

    override def equals(other: Any): Boolean = other match {
      case that: Pool =>
        this.avroPlaceholder.toString == that.avroPlaceholder.toString  &&
          (this.init map {case (k, v) => (k, convertFromJson(v))}) == (that.init map {case (k, v) => (k, convertFromJson(v))})  &&
          this.shared == that.shared  &&
          this.rollback == that.rollback  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((avroPlaceholder.toString, this.init map {case (k, v) => (k, convertFromJson(v))}, shared, rollback))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = Pool.Context()
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("type", convertFromJson(avroPlaceholder.toString))

      val jsonInits = factory.objectNode
      for ((name, value) <- init)
        jsonInits.put(name, convertFromJson(value))
      out.put("init", jsonInits)

      out.put("shared", shared)
      out.put("rollback", rollback)
      out
    }
  }
  object Pool {
    case class Context() extends AstContext
  }

  trait Argument extends Ast
  trait Expression extends Argument
  trait LiteralValue extends Expression
  trait PathIndex
  case class ArrayIndex(i: TaskResult, t: AvroType) extends PathIndex
  case class MapIndex(k: TaskResult, t: AvroType) extends PathIndex
  case class RecordIndex(f: String, t: AvroType) extends PathIndex

  trait HasPath {
    def path: Seq[Expression]
    def pos: Option[String]

    def walkPath(avroType: AvroType, task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AvroType, Set[String], Seq[PathIndex]) = {
      val calls = mutable.Set[String]()
      val scope = symbolTable.newScope(true, true)
      var walkingType = avroType

      val pathIndexes =
        for ((expr, indexIndex) <- path.zipWithIndex) yield {
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, scope, functionTable)
          calls ++= exprContext.calls

          (walkingType, exprContext.retType) match {
            case (x: AvroArray, y: AvroLong) => walkingType = x.items;  ArrayIndex(exprResult, walkingType)
            case (x: AvroArray, y: AvroInt) => walkingType = x.items;  ArrayIndex(exprResult, walkingType)
            case (x: AvroArray, y) => throw new PFASemanticException("path index for an array must resolve to a long or int; item %d is a %s".format(indexIndex, y.toString), pos)

            case (x: AvroMap, y: AvroString) => walkingType = x.values;  MapIndex(exprResult, walkingType)
            case (x: AvroMap, y) => throw new PFASemanticException("path index for a map must resolve to a string; item %d is a %s".format(indexIndex, y.toString), pos)

            case (x: AvroRecord, y: AvroString) => {
              val name: String = exprContext match {
                case LiteralString.Context(_, _, z) => z
                case Literal.Context(t: AvroString, _, z) => convertFromJson(z).asInstanceOf[String]
                case z => throw new PFASemanticException("path index for record %s must be a literal string; item %d is an object of type %s".format(x.toString, indexIndex, z.retType.toString), pos)
              }
                (x.fields find {_.name == name}) match {
                case Some(field) => walkingType = field.avroType;  RecordIndex(name, walkingType)
                case None => throw new PFASemanticException("record %s has no field named \"%s\" (path index %d)".format(x.toString, name, indexIndex), pos)
              }
            }
            case (x: AvroRecord, y) => throw new PFASemanticException("path index for a record must be a string; item %d is a %s".format(indexIndex, y.toString), pos)

            case (x, _) => throw new PFASemanticException("path item %d is a %s, which cannot be indexed".format(indexIndex, x.toString), pos)
          }
        }

      (walkingType, calls.toSet, pathIndexes)
    }
  }

  case class FcnDef(paramsPlaceholder: Seq[(String, AvroPlaceholder)], retPlaceholder: AvroPlaceholder, body: Seq[Expression], pos: Option[String] = None) extends Argument {
    def params: Seq[(String, AvroType)] = paramsPlaceholder.map({case (k, v) => (k, v.avroType)})
    def ret: AvroType = retPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: FcnDef =>
        this.paramsPlaceholder.corresponds(that.paramsPlaceholder)({case ((k1, v1), (k2, v2)) => k1 == k2  &&  v1.toString == v2.toString})  &&  this.ret == that.ret  &&  this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((paramsPlaceholder.map({case (k1, v1) => (k1, v1.toString)})), ret, body)

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("function's \"do\" list must contain least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      if (paramsPlaceholder.size > 22)
        throw new PFASemanticException("function can have at most 22 parameters", pos)

      val scope = symbolTable.newScope(true, false)
      for ((name, avroType) <- params) {
        if (!validSymbolName(name))
          throw new PFASemanticException("\"%s\" is not a valid parameter name".format(name), pos)
        scope.put(name, avroType)
      }

      val results: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, scope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}

      if (!ret.accepts(results.last._1.retType))
        throw new PFASemanticException("function's inferred return type is %s but its declared return type is %s".format(results.last._1.retType, ret), pos)

      val context = FcnDef.Context(FcnType(params map {_._2}, ret), results.map(_._1.calls).flatten.toSet, params, ret, scope.inThisScope, results map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonParams = factory.arrayNode
      for ((n, p) <- paramsPlaceholder) {
        val pair = factory.objectNode
        pair.put(n, convertFromJson(p.toString))
        jsonParams.add(pair)
      }
      out.put("params", jsonParams)

      out.put("ret", convertFromJson(ret.toString))

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)

      out
    }
  }
  object FcnDef {
    case class Context(fcnType: FcnType, calls: Set[String], params: Seq[(String, AvroType)], ret: AvroType, symbols: Map[String, AvroType], exprs: Seq[TaskResult]) extends AstContext
  }

  case class FcnRef(name: String, pos: Option[String] = None) extends Argument {
    override def equals(other: Any): Boolean = other match {
      case that: FcnRef => this.name == that.name  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[String](name))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val fcn = functionTable.functions.get(name) match {
        case Some(x) => x
        case None => throw new PFASemanticException("unknown function \"%s\"".format(name), pos)
      }

      val fcnType = fcn.sig match {
        case Sig(params, ret) => FcnType(params map {case (k, p) => P.toType(p)}, P.mustBeAvro(P.toType(ret)))
        case _ => throw new PFASemanticException("only one-signature functions without constraints can be referenced (wrap \"%s\" in a function definition with the desired signature)".format(name), pos)
      }

      val context = FcnRef.Context(fcnType, Set(name), fcn)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("fcnref", name)
      out
    }
  }
  object FcnRef {
    case class Context(fcnType: FcnType, calls: Set[String], fcn: Fcn) extends AstContext
  }

  case class Call(name: String, args: Seq[Argument], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Call => this.name == that.name  &&  this.args == that.args  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((name, args))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        args.flatMap(_.collect(pf))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val fcn = functionTable.functions.get(name) match {
        case Some(x) => x
        case None => throw new PFASemanticException("unknown function \"%s\"".format(name), pos)
      }

      val scope = symbolTable.newScope(true, true)
      val argResults: Seq[(AstContext, TaskResult)] = args.map(_.walk(task, scope, functionTable))

      val calls = mutable.Set[String](name)
      val argTypes: Seq[Type] =
        for ((ctx, res) <- argResults) yield {
          ctx match {
            case exprCtx: ExpressionContext => calls ++= exprCtx.calls;  exprCtx.retType
            case FcnDef.Context(fcnType, fcnCalls, _, _, _, _) => calls ++= fcnCalls;  fcnType
            case FcnRef.Context(fcnType, fcnCalls, _) => calls ++= fcnCalls;  fcnType
          }
        }

      val context =
        fcn.sig.accepts(argTypes) match {
          case Some((paramTypes, retType)) => {
            val argContexts = argResults map { _._1 }
            val argTaskResults = argResults map { _._2 } toArray

            for ((a, i) <- args.zipWithIndex) a match {
              case x: FcnRef => argTaskResults(i) = task(argContexts(i), Some(paramTypes(i)))
              case _ =>
            }

            Call.Context(retType, calls.toSet, fcn, argTaskResults.toList, argContexts)
          }
          case None => throw new PFASemanticException("parameters of function \"%s\" do not accept [%s]".format(name, argTypes.mkString(",")), pos)
        }
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonArgs = factory.arrayNode
      for (expr <- args)
        jsonArgs.add(expr.jsonNode)
      out.put(name, jsonArgs)

      out
    }
  }
  object Call {
    case class Context(retType: AvroType, calls: Set[String], fcn: Fcn, args: Seq[TaskResult], argContext: Seq[AstContext]) extends ExpressionContext
  }

  case class Ref(name: String, pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Ref => this.name == that.name  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[String](name))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      if (symbolTable.get(name) == None)
        throw new PFASemanticException("unknown symbol \"%s\"".format(name), pos)
      val context = Ref.Context(symbolTable(name), Set[String](), name)
      (context, task(context))
    }

    override def jsonNode: JsonNode = new TextNode(name)
  }
  object Ref {
    case class Context(retType: AvroType, calls: Set[String], name: String) extends ExpressionContext
  }

  case class LiteralNull(pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralNull => true  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Null](null))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralNull.Context(AvroNull(), Set[String](LiteralNull.desc))
      (context, task(context))
    }

    override def jsonNode: JsonNode = NullNode.getInstance
  }
  object LiteralNull {
    val desc = "(null)"
    case class Context(retType: AvroType, calls: Set[String]) extends ExpressionContext
  }

  case class LiteralBoolean(value: Boolean, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralBoolean => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Boolean](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralBoolean.Context(AvroBoolean(), Set[String](LiteralBoolean.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = if (value) BooleanNode.getTrue else BooleanNode.getFalse
  }
  object LiteralBoolean {
    val desc = "(boolean)"
    case class Context(retType: AvroType, calls: Set[String], value: Boolean) extends ExpressionContext
  }

  case class LiteralInt(value: Int, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralInt => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Int](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralInt.Context(AvroInt(), Set[String](LiteralInt.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = new IntNode(value)
  }
  object LiteralInt {
    val desc = "(int)"
    case class Context(retType: AvroType, calls: Set[String], value: Int) extends ExpressionContext
  }

  case class LiteralLong(value: Long, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralLong => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Long](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralLong.Context(AvroLong(), Set[String](LiteralLong.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode =
      if (java.lang.Integer.MIN_VALUE <= value  &&  value <= java.lang.Integer.MAX_VALUE) {
        val factory = JsonNodeFactory.instance
        val out = factory.objectNode
        out.put("long", new LongNode(value))
        out
      }
    else
      new LongNode(value)
  }
  object LiteralLong {
    val desc = "(long)"
    case class Context(retType: AvroType, calls: Set[String], value: Long) extends ExpressionContext
  }

  case class LiteralFloat(value: Float, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralFloat => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Float](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralFloat.Context(AvroFloat(), Set[String](LiteralFloat.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("float", new DoubleNode(value))
      out
    }
  }
  object LiteralFloat {
    val desc = "(float)"
    case class Context(retType: AvroType, calls: Set[String], value: Float) extends ExpressionContext
  }

  case class LiteralDouble(value: Double, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralDouble => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Double](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralDouble.Context(AvroDouble(), Set[String](LiteralDouble.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = new DoubleNode(value)
  }
  object LiteralDouble {
    val desc = "(double)"
    case class Context(retType: AvroType, calls: Set[String], value: Double) extends ExpressionContext
  }

  case class LiteralString(value: String, pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralString => this.value == that.value  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[String](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralString.Context(AvroString(), Set[String](LiteralString.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("string", new TextNode(value))
      out
    }
  }
  object LiteralString {
    val desc = "(string)"
    case class Context(retType: AvroType, calls: Set[String], value: String) extends ExpressionContext
  }

  case class LiteralBase64(value: Array[Byte], pos: Option[String] = None) extends LiteralValue {
    override def equals(other: Any): Boolean = other match {
      case that: LiteralBase64 => this.value.sameElements(that.value)  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Array[Byte]](value))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = LiteralBase64.Context(AvroBytes(), Set[String](LiteralBase64.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("base64", new BinaryNode(value))
      out
    }
  }
  object LiteralBase64 {
    val desc = "(bytes)"
    case class Context(retType: AvroType, calls: Set[String], value: Array[Byte]) extends ExpressionContext
  }

  case class Literal(avroPlaceholder: AvroPlaceholder, value: String, pos: Option[String] = None) extends LiteralValue {
    def avroType: AvroType = avroPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: Literal => this.avroPlaceholder.toString == that.avroPlaceholder.toString  &&  convertFromJson(this.value) == convertFromJson(that.value)  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((avroPlaceholder.toString, convertFromJson(value)))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = Literal.Context(avroType, Set[String](Literal.desc), value)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("type", convertFromJson(avroType.toString))
      out.put("value", convertFromJson(value))
      out
    }
  }
  object Literal {
    val desc = "(literal)"
    case class Context(retType: AvroType, calls: Set[String], value: String) extends ExpressionContext
  }

  case class NewObject(fields: Map[String, Expression], avroPlaceholder: AvroPlaceholder, avroTypeBuilder: AvroTypeBuilder, pos: Option[String] = None) extends Expression {
    def avroType: AvroType = avroPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: NewObject => this.fields == that.fields  &&  this.avroType == that.avroType  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((fields, avroType))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        fields.values.flatMap(_.collect(pf))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val fieldNameTypeExpr: Seq[(String, AvroType, TaskResult)] =
        for ((name, expr) <- fields.toList) yield {
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, symbolTable, functionTable)
          calls ++= exprContext.calls
          (name, exprContext.retType, exprResult)
        }

      val retType =
        avroType match {
          case record @ AvroRecord(_, name, namespace, _, _) => {
            val newType = AvroRecord(for ((n, fieldType, _) <- fieldNameTypeExpr) yield AvroField(n, fieldType), name, namespace)
            if (!record.accepts(newType)  ||  !newType.accepts(record))
              throw new PFASemanticException("record constructed with \"new\" has incorrectly-typed fields: %s rather than %s".format(newType, record), pos)
            record
          }
          case AvroMap(values) => {
            if (!fieldNameTypeExpr.isEmpty) {
              val newType = try {
                LabelData.broadestType(fieldNameTypeExpr map {_._2} toList)
              }
              catch {
                case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
              }
              if (!values.accepts(newType))
                throw new PFASemanticException("map constructed with \"new\" has incorrectly-typed items: %s rather than %s".format(newType, values), pos)
            }
            avroType
          }
          case x => throw new PFASemanticException("object constructed with \"new\" must have record or map type, not %s".format(x), pos)
        }

      val context = NewObject.Context(retType, calls.toSet + NewObject.desc, fieldNameTypeExpr map {x => (x._1, x._3)} toMap)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonFields = factory.objectNode
      for ((name, expr) <- fields)
        jsonFields.put(name, expr.jsonNode)
      out.put("new", jsonFields)

      out.put("type", convertFromJson(avroPlaceholder.toString))
      out
    }
  }
  object NewObject {
    val desc = "new (object)"
    case class Context(retType: AvroType, calls: Set[String], fields: Map[String, TaskResult]) extends ExpressionContext
  }

  case class NewArray(items: Seq[Expression], avroPlaceholder: AvroPlaceholder, avroTypeBuilder: AvroTypeBuilder, pos: Option[String] = None) extends Expression {
    def avroType: AvroType = avroPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: NewArray => this.items == that.items  &&  this.avroType == that.avroType  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((items, avroType))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        items.flatMap(_.collect(pf))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val itemTypeExpr: Seq[(AvroType, TaskResult)] =
        for (expr <- items) yield {
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, symbolTable, functionTable)
          calls ++= exprContext.calls
          (exprContext.retType, exprResult)
        }

      val retType =
        avroType match {
          case AvroArray(itms) => {
            if (!itemTypeExpr.isEmpty) {
              val newType = try {
                LabelData.broadestType(itemTypeExpr map {_._1} toList)
              }
              catch {
                case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
              }
              if (!itms.accepts(newType))
                throw new PFASemanticException("array constructed with \"new\" has incorrectly-typed items: %s rather than %s".format(newType, itms), pos)
            }
            avroType
          }
          case x => throw new PFASemanticException("array constructed with \"new\" must have array type, not %s".format(x), pos)
        }

      val context = NewArray.Context(retType, calls.toSet + NewArray.desc, itemTypeExpr map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonItems = factory.arrayNode
      for (expr <- items)
        jsonItems.add(expr.jsonNode)
      out.put("new", jsonItems)

      out.put("type", convertFromJson(avroPlaceholder.toString))
      out
    }
  }
  object NewArray {
    val desc = "new (array)"
    case class Context(retType: AvroType, calls: Set[String], items: Seq[TaskResult]) extends ExpressionContext
  }

  case class Do(body: Seq[Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Do => this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Seq[Expression]](body))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" block must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val scope = symbolTable.newScope(false, false)
      val results: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, scope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val context = Do.Context(results.last._1.retType, results.map(_._1.calls).flatten.toSet + Do.desc, scope.inThisScope, results map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)
      out
    }
  }
  object Do {
    val desc = "do"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], exprs: Seq[TaskResult]) extends ExpressionContext
  }

  case class Let(values: Map[String, Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Let => this.values == that.values  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Map[String, Expression]](values))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        values.values.flatMap(_.collect(pf))

    if (values.size < 1)
      throw new PFASyntaxException("\"let\" must contain at least one declaration", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      if (symbolTable.sealedWithin)
        throw new PFASemanticException("new variable bindings are forbidden in this scope, but you can wrap your expression with \"do\" to make temporary variables", pos)

      val calls = mutable.Set[String]()

      val nameTypeExpr: Seq[(String, AvroType, TaskResult)] =
        for ((name, expr) <- values.toList) yield {
          if (symbolTable.get(name) != None)
            throw new PFASemanticException("symbol \"%s\" may not be redeclared or shadowed".format(name), pos)

          if (!validSymbolName(name))
            throw new PFASemanticException("\"%s\" is not a valid symbol name".format(name), pos)

          val scope = symbolTable.newScope(true, true)
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, scope, functionTable)
          calls ++= exprContext.calls

          symbolTable.put(name, exprContext.retType)
          (name, exprContext.retType, exprResult)
        }

      val context = Let.Context(AvroNull(), calls.toSet + Let.desc, nameTypeExpr)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonValues = factory.objectNode
      for ((name, expr) <- values)
        jsonValues.put(name, expr.jsonNode)
      out.put("let", jsonValues)

      out
    }
  }
  object Let {
    val desc = "let"
    case class Context(retType: AvroType, calls: Set[String], nameTypeExpr: Seq[(String, AvroType, TaskResult)]) extends ExpressionContext
  }

  case class SetVar(values: Map[String, Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: SetVar => this.values == that.values  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[Map[String, Expression]](values))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        values.values.flatMap(_.collect(pf))

    if (values.size < 1)
      throw new PFASyntaxException("\"set\" must contain at least one assignment", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val nameExpr: Seq[(String, TaskResult)] =
        for ((name, expr) <- values.toList) yield {
          if (symbolTable.get(name) == None)
            throw new PFASemanticException("unknown symbol \"%s\" cannot be assigned with \"set\" (use \"let\" to declare a new symbol)".format(name), pos)
          else if (!symbolTable.writable(name))
            throw new PFASemanticException("symbol \"%s\" belongs to a sealed enclosing scope; it cannot be modified within this block)".format(name), pos)

          val scope = symbolTable.newScope(true, true)
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, scope, functionTable)
          calls ++= exprContext.calls

          if (!symbolTable(name).accepts(exprContext.retType))
            throw new PFASemanticException("symbol \"%s\" was declared as %s; it cannot be re-assigned as %s".format(name, symbolTable(name), exprContext.retType), pos)

          (name, exprResult)
        }

      val context = SetVar.Context(AvroNull(), calls.toSet + SetVar.desc, nameExpr)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonValues = factory.objectNode
      for ((name, expr) <- values)
        jsonValues.put(name, expr.jsonNode)
      out.put("set", jsonValues)

      out
    }
  }
  object SetVar {
    val desc = "set"
    case class Context(retType: AvroType, calls: Set[String], nameExpr: Seq[(String, TaskResult)]) extends ExpressionContext
  }

  case class AttrGet(attr: String, path: Seq[Expression], pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: AttrGet => this.attr == that.attr  &&  this.path == that.path  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((attr, path))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf))

    if (path.size < 1)
      throw new PFASyntaxException("attr path must have at least one key", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val attrType = symbolTable.get(attr) match {
        case Some(x) => x.avroType
        case None => throw new PFASemanticException("unknown symbol \"%s\"".format(attr), pos)
      }

      attrType match {
        case _: AvroArray | _: AvroMap | _: AvroRecord =>
        case _ => throw new PFASemanticException("symbol \"%s\" is not an array, map, or record".format(attr), pos)
      }

      val (retType, calls, pathResult) = walkPath(attrType, task, symbolTable, functionTable)
      val context = AttrGet.Context(retType, calls + AttrGet.desc, attr, attrType, pathResult)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("attr", attr)
      val jsonPath = factory.arrayNode
      for (expr <- path)
        jsonPath.add(expr.jsonNode)
      out.put("path", jsonPath)
      out
    }
  }
  object AttrGet {
    val desc = "attr"
    case class Context(retType: AvroType, calls: Set[String], attr: String, attrType: AvroType, path: Seq[PathIndex]) extends ExpressionContext
  }

  case class AttrTo(attr: String, path: Seq[Expression], to: Argument, pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: AttrTo => this.attr == that.attr  &&  this.path == that.path  &&  this.to == that.to  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((attr, path, to))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf)) ++
        to.collect(pf)

    if (path.size < 1)
      throw new PFASyntaxException("attr path must have at least one key", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val attrType = symbolTable.get(attr) match {
        case Some(x) => x.avroType
        case None => throw new PFASemanticException("unknown symbol \"%s\"".format(attr), pos)
      }

      attrType match {
        case _: AvroArray | _: AvroMap | _: AvroRecord =>
        case _ => throw new PFASemanticException("symbol \"%s\" is not an array, map, or record".format(attr), pos)
      }

      val (setType, calls, pathResult) = walkPath(attrType, task, symbolTable, functionTable)

      val (toContext, toResult) = to.walk(task, symbolTable, functionTable)

      val context =
        toContext match {
          case toCtx: ExpressionContext => {
            if (!setType.accepts(toCtx.retType))
              throw new PFASemanticException("attr-and-path has type %s but attempting to assign with type %s".format(setType.toString, toCtx.retType.toString), pos)
            AttrTo.Context(attrType, calls ++ toCtx.calls + AttrTo.desc, attr, attrType, setType, pathResult, toResult, toCtx.retType)
          }

          case FcnDef.Context(fcnType, fcnCalls, _, _, _, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("attr-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            AttrTo.Context(attrType, calls ++ fcnCalls + AttrTo.desc, attr, attrType, setType, pathResult, toResult, fcnType)
          }

          case FcnRef.Context(fcnType, fcnCalls, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("attr-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            AttrTo.Context(attrType, calls ++ fcnCalls + AttrTo.desc, attr, attrType, setType, pathResult, task(toContext, Some(fcnType)), fcnType)
          }
        }
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("attr", attr)
      val jsonPath = factory.arrayNode
      for (expr <- path)
        jsonPath.add(expr.jsonNode)
      out.put("path", jsonPath)
      out.put("to", to.jsonNode)
      out
    }
  }
  object AttrTo {
    val desc = "attr-to"
    case class Context(retType: AvroType, calls: Set[String], attr: String, attrType: AvroType, setType: AvroType, path: Seq[PathIndex], to: TaskResult, toType: Type) extends ExpressionContext
  }

  case class CellGet(cell: String, path: Seq[Expression], pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: CellGet => this.cell == that.cell  &&  this.path == that.path  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((cell, path))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val (cellType, shared) = symbolTable.cell(cell) match {
        case Some(x) => (x.avroType, x.shared)
        case None => throw new PFASemanticException("no cell named \"%s\"".format(cell), pos)
      }

      val (retType, calls, pathResult) = walkPath(cellType, task, symbolTable, functionTable)
      val context = CellGet.Context(retType, calls + CellGet.desc, cell, cellType, pathResult, shared)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("cell", cell)
      if (!path.isEmpty) {
        val jsonPath = factory.arrayNode
        for (expr <- path)
          jsonPath.add(expr.jsonNode)
        out.put("path", jsonPath)
      }
      out
    }
  }
  object CellGet {
    val desc = "cell"
    case class Context(retType: AvroType, calls: Set[String], cell: String, cellType: AvroType, path: Seq[PathIndex], shared: Boolean) extends ExpressionContext
  }

  case class CellTo(cell: String, path: Seq[Expression], to: Argument, pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: CellTo => this.cell == that.cell  &&  this.path == that.path  &&  this.to == that.to  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((cell, path, to))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf)) ++
        to.collect(pf)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val (cellType, shared) = symbolTable.cell(cell) match {
        case Some(x) => (x.avroType, x.shared)
        case None => throw new PFASemanticException("no cell named \"%s\"".format(cell), pos)
      }

      val (setType, calls, pathResult) = walkPath(cellType, task, symbolTable, functionTable)

      val (toContext, toResult) = to.walk(task, symbolTable, functionTable)

      val context =
        toContext match {
          case toCtx: ExpressionContext => {
            if (!setType.accepts(toCtx.retType))
              throw new PFASemanticException("cell-and-path has type %s but attempting to assign with type %s".format(setType.toString, toCtx.retType.toString), pos)
            CellTo.Context(AvroNull(), calls ++ toCtx.calls + CellTo.desc, cell, cellType, setType, pathResult, toResult, toCtx.retType, shared)
          }
          case FcnDef.Context(fcnType, fcnCalls, _, _, _, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("cell-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            CellTo.Context(AvroNull(), calls ++ fcnCalls + CellTo.desc, cell, cellType, setType, pathResult, toResult, fcnType, shared)
          }
          case FcnRef.Context(fcnType, fcnCalls, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("cell-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            CellTo.Context(AvroNull(), calls ++ fcnCalls + CellTo.desc, cell, cellType, setType, pathResult, task(toContext, Some(fcnType)), fcnType, shared)
          }
        }
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("cell", cell)
      if (!path.isEmpty) {
        val jsonPath = factory.arrayNode
        for (expr <- path)
          jsonPath.add(expr.jsonNode)
        out.put("path", jsonPath)
      }
      out.put("to", to.jsonNode)
      out
    }
  }
  object CellTo {
    val desc = "cell-to"
    case class Context(retType: AvroType, calls: Set[String], cell: String, cellType: AvroType, setType: AvroType, path: Seq[PathIndex], to: TaskResult, toType: Type, shared: Boolean) extends ExpressionContext
  }

  case class PoolGet(pool: String, path: Seq[Expression], pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: PoolGet => this.pool == that.pool  &&  this.path == that.path  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((pool, path))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf))

    if (path.size < 1)
      throw new PFASyntaxException("pool path must have at least one key", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val (poolType, shared) = symbolTable.pool(pool) match {
        case Some(x) => (x.avroType, x.shared)
        case None => throw new PFASemanticException("no pool named \"%s\"".format(pool), pos)
      }

      val (retType, calls, pathResult) = walkPath(AvroMap(poolType), task, symbolTable, functionTable)
      val context = PoolGet.Context(retType, calls + PoolGet.desc, pool, pathResult, shared)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("pool", pool)
      val jsonPath = factory.arrayNode
      for (expr <- path)
        jsonPath.add(expr.jsonNode)
      out.put("path", jsonPath)
      out
    }
  }
  object PoolGet {
    val desc = "pool"
    case class Context(retType: AvroType, calls: Set[String], pool: String, path: Seq[PathIndex], shared: Boolean) extends ExpressionContext
  }

  case class PoolTo(pool: String, path: Seq[Expression], to: Argument, init: Option[Expression], pos: Option[String] = None) extends Expression with HasPath {
    override def equals(other: Any): Boolean = other match {
      case that: PoolTo => this.pool == that.pool  &&  this.path == that.path  &&  this.to == that.to  &&  this.init == that.init  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((pool, path, to, init))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        path.flatMap(_.collect(pf)) ++
        to.collect(pf) ++
        (if (init == None) List[X]() else init.get.collect(pf))

    if (path.size < 1)
      throw new PFASyntaxException("pool path must have at least one key", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val (poolType, shared) = symbolTable.pool(pool) match {
        case Some(x) => (x.avroType, x.shared)
        case None => throw new PFASemanticException("no pool named \"%s\"".format(pool), pos)
      }

      val (setType, calls, pathResult) = walkPath(AvroMap(poolType), task, symbolTable, functionTable)

      val (toContext, toResult) = to.walk(task, symbolTable, functionTable)

      val context =
        toContext match {
          case toCtx: ExpressionContext => {
            if (!setType.accepts(toCtx.retType))
              throw new PFASemanticException("pool-and-path has type %s but attempting to assign with type %s".format(setType.toString, toCtx.retType.toString), pos)
            if (init != None)
              throw new PFASemanticException("if \"to\" is an expression, \"init\" is not allowed", pos)
            PoolTo.Context(AvroNull(), calls ++ toCtx.calls + PoolTo.desc, pool, poolType, setType, pathResult, toResult, toCtx.retType, toResult, shared)
          }

          case FcnDef.Context(fcnType, fcnCalls, _, _, _, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("pool-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            if (init == None)
              throw new PFASemanticException("if \"to\" is a function, \"init\" must be provided", pos)
            val (initContext: ExpressionContext, initResult) = init.get.walk(task, symbolTable, functionTable)
            if (!setType.accepts(initContext.retType))
              throw new PFASemanticException("pool-and-path has type %s but attempting to init with type %s".format(setType.toString, initContext.retType.toString), pos)
            PoolTo.Context(AvroNull(), calls ++ fcnCalls + PoolTo.desc, pool, poolType, setType, pathResult, toResult, fcnType, initResult, shared)
          }

          case FcnRef.Context(fcnType, fcnCalls, _) => {
            if (!FcnType(List(setType), setType).accepts(fcnType))
              throw new PFASemanticException("pool-and-path has type %s but attempting to assign with a function of type %s".format(setType.toString, fcnType.toString), pos)
            if (init == None)
              throw new PFASemanticException("if \"to\" is a function, \"init\" must be provided", pos)
            val (initContext: ExpressionContext, initResult) = init.get.walk(task, symbolTable, functionTable)
            if (!setType.accepts(initContext.retType))
              throw new PFASemanticException("pool-and-path has type %s but attempting to init with type %s".format(setType.toString, initContext.retType.toString), pos)
            PoolTo.Context(AvroNull(), calls ++ fcnCalls + PoolTo.desc, pool, poolType, setType, pathResult, task(toContext, Some(fcnType)), fcnType, initResult, shared)
          }
        }
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("pool", pool)
      val jsonPath = factory.arrayNode
      for (expr <- path)
        jsonPath.add(expr.jsonNode)
      out.put("path", jsonPath)
      init foreach { x => out.put("init", x.jsonNode) }
      out.put("to", to.jsonNode)
      out
    }
  }
  object PoolTo {
    val desc = "pool-to"
    case class Context(retType: AvroType, calls: Set[String], pool: String, poolType: AvroType, setType: AvroType, path: Seq[PathIndex], to: TaskResult, toType: Type, init: TaskResult, shared: Boolean) extends ExpressionContext
  }

  case class If(predicate: Expression, thenClause: Seq[Expression], elseClause: Option[Seq[Expression]], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: If =>
        this.predicate == that.predicate  &&  this.thenClause == that.thenClause  &&  this.elseClause == that.elseClause  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((predicate, thenClause, elseClause))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        predicate.collect(pf) ++
        thenClause.flatMap(_.collect(pf)) ++
        (if (elseClause == None) List[X]() else elseClause.get.flatMap(_.collect(pf)))

    if (thenClause.size < 1)
      throw new PFASyntaxException("\"then\" clause must contain at least one expression", pos)

    if (elseClause != None  &&  elseClause.get.size < 1)
      throw new PFASyntaxException("\"else\" clause must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val predScope = symbolTable.newScope(true, true)
      val (predContext: ExpressionContext, predResult) = predicate.walk(task, predScope, functionTable)
      if (!AvroBoolean().accepts(predContext.retType))
        throw new PFASemanticException("\"if\" predicate should be boolean, but is " + predContext.retType, pos)
      calls ++= predContext.calls

      val thenScope = symbolTable.newScope(false, false)
      val thenResults: Seq[(ExpressionContext, TaskResult)] = thenClause.map(_.walk(task, thenScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- thenResults)
        calls ++= exprCtx.calls
     
      val (retType, elseTaskResults, elseSymbols) = 
        elseClause match {
          case Some(clause) => {
            val elseScope = symbolTable.newScope(false, false)

            val elseResults = clause.map(_.walk(task, elseScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
            for ((exprCtx, _) <- elseResults)
              calls ++= exprCtx.calls

            val thenType = thenResults.last._1.retType
            val elseType = elseResults.last._1.retType
            val retType =
              try {
                P.mustBeAvro(LabelData.broadestType(List(thenType, elseType)))
              }
              catch {
                case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
              }

            (retType, Some(elseResults map {_._2}), Some(elseScope.inThisScope))
          }
          case None => (AvroNull(), None, None)
        }

      val context = If.Context(retType, calls.toSet + If.desc, thenScope.inThisScope, predResult, thenResults map {_._2}, elseSymbols, elseTaskResults)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      out.put("if", predicate.jsonNode)

      val jsonThenClause = factory.arrayNode
      for (expr <- thenClause)
        jsonThenClause.add(expr.jsonNode)
      out.put("then", jsonThenClause)

      elseClause match {
        case Some(clause) => {
          val jsonElseClause = factory.arrayNode
          for (expr <- clause)
            jsonElseClause.add(expr.jsonNode)
          out.put("else", jsonElseClause)
        }
        case None =>
      }

      out
    }
  }
  object If {
    val desc = "if"
    case class Context(retType: AvroType, calls: Set[String], thenSymbols: Map[String, AvroType], predicate: TaskResult, thenClause: Seq[TaskResult], elseSymbols: Option[Map[String, AvroType]], elseClause: Option[Seq[TaskResult]]) extends ExpressionContext
  }

  case class Cond(ifthens: Seq[If], elseClause: Option[Seq[Expression]], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Cond =>
        this.ifthens == that.ifthens  &&  this.elseClause == that.elseClause  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((ifthens, elseClause))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        ifthens.flatMap(_.collect(pf)) ++
        (if (elseClause == None) List[X]() else elseClause.get.flatMap(_.collect(pf)))

    if (ifthens.size < 1)
      throw new PFASyntaxException("\"cond\" must contain at least one predicate-block pair", pos)

    for (If(_, thenClause, _, ifpos) <- ifthens)
      if (thenClause.size < 1)
        throw new PFASyntaxException("\"then\" clause must contain at least one expression", ifpos)

    if (elseClause != None  &&  elseClause.get.size < 1)
      throw new PFASyntaxException("\"else\" clause must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val withoutElse: Seq[Cond.WalkBlock] =
        for (If(predicate, thenClause, _, ifpos) <- ifthens) yield {
          val predScope = symbolTable.newScope(true, true)
          val (predContext: ExpressionContext, predResult) = predicate.walk(task, predScope, functionTable)
          if (!AvroBoolean().accepts(predContext.retType))
            throw new PFASemanticException("\"if\" predicate should be boolean, but is " + predContext.retType, ifpos)
          calls ++= predContext.calls

          val thenScope = symbolTable.newScope(false, false)
          val thenResults: Seq[(ExpressionContext, TaskResult)] = thenClause.map(_.walk(task, thenScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
          for ((exprCtx, _) <- thenResults)
            calls ++= exprCtx.calls

          Cond.WalkBlock(thenResults.last._1.retType, thenScope.inThisScope, Some(predResult), thenResults map {_._2})
        }

      val walkBlocks = elseClause match {
        case Some(clause) => {
          val elseScope = symbolTable.newScope(false, false)

          val elseResults = clause.map(_.walk(task, elseScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
          for ((exprCtx, _) <- elseResults)
            calls ++= exprCtx.calls

          withoutElse :+ Cond.WalkBlock(elseResults.last._1.retType, elseScope.inThisScope, None, elseResults map {_._2})
        }
        case None => withoutElse
      }

      val retType =
        if (elseClause == None)
          AvroNull()
        else
          try {
            LabelData.broadestType(walkBlocks.collect({case Cond.WalkBlock(t, _, _, _) => t.asInstanceOf[AvroType]}).toList)
          }
          catch {
            case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
          }

      val context = Cond.Context(retType.asInstanceOf[AvroType], calls.toSet + Cond.desc, (elseClause != None), walkBlocks)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonIfthens = factory.arrayNode
      for (ifthen <- ifthens)
        jsonIfthens.add(ifthen.jsonNode)
      out.put("cond", jsonIfthens)

      elseClause match {
        case Some(clause) => {
          val jsonElseClause = factory.arrayNode
          for (expr <- clause)
            jsonElseClause.add(expr.jsonNode)
          out.put("else", jsonElseClause)
        }
        case None =>
      }

      out
    }
  }
  object Cond {
    val desc = "cond"
    case class WalkBlock(retType: AvroType, symbols: Map[String, AvroType], pred: Option[TaskResult], exprs: Seq[TaskResult])
    case class Context(retType: AvroType, calls: Set[String], complete: Boolean, walkBlocks: Seq[WalkBlock]) extends ExpressionContext
  }

  case class While(predicate: Expression, body: Seq[Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: While =>
        this.predicate == that.predicate  &&  this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((predicate, body))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        predicate.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" block must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()
      val loopScope = symbolTable.newScope(false, false)
      val predScope = loopScope.newScope(true, true)

      val (predContext: ExpressionContext, predResult) = predicate.walk(task, predScope, functionTable)
      if (!AvroBoolean().accepts(predContext.retType))
        throw new PFASemanticException("\"while\" predicate should be boolean, but is " + predContext.retType, pos)
      calls ++= predContext.calls

      val loopResults: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, loopScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- loopResults)
        calls ++= exprCtx.calls

      val context = While.Context(AvroNull(), calls.toSet + While.desc, loopScope.inThisScope, predResult, loopResults map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("while", predicate.jsonNode)

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)
      out
    }
  }
  object While {
    val desc = "while"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], predicate: TaskResult, loopBody: Seq[TaskResult]) extends ExpressionContext
  }

  case class DoUntil(body: Seq[Expression], predicate: Expression, pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: DoUntil =>
        this.body == that.body  &&  this.predicate == that.predicate  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((body, predicate))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        body.flatMap(_.collect(pf)) ++
        predicate.collect(pf)

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" block must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()
      val loopScope = symbolTable.newScope(false, false)
      val predScope = loopScope.newScope(true, true)

      val loopResults: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, loopScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- loopResults)
        calls ++= exprCtx.calls

      val (predContext: ExpressionContext, predResult) = predicate.walk(task, predScope, functionTable)
      if (!AvroBoolean().accepts(predContext.retType))
        throw new PFASemanticException("\"until\" predicate should be boolean, but is " + predContext.retType, pos)
      calls ++= predContext.calls

      val context = DoUntil.Context(AvroNull(), calls.toSet + DoUntil.desc, loopScope.inThisScope, loopResults map {_._2}, predResult)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)

      out.put("until", predicate.jsonNode)
      out
    }
  }
  object DoUntil {
    val desc = "do-until"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], loopBody: Seq[TaskResult], predicate: TaskResult) extends ExpressionContext
  }

  case class For(init: Map[String, Expression], until: Expression, step: Map[String, Expression], body: Seq[Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: For =>
        this.init == that.init  &&  this.until == that.until  &&  this.step == that.step  &&  this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((init, until, step, body))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        init.values.flatMap(_.collect(pf)) ++
        until.collect(pf) ++
        step.values.flatMap(_.collect(pf)) ++
        body.flatMap(_.collect(pf))

    if (init.size < 1)
      throw new PFASyntaxException("\"for\" must contain at least one declaration", pos)

    if (step.size < 1)
      throw new PFASyntaxException("\"step\" must contain at least one assignment", pos)

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" must contain at least one statement", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()
      val loopScope = symbolTable.newScope(false, false)

      val initNameTypeExpr: Seq[(String, AvroType, TaskResult)] =
        for ((name, expr) <- init.toList) yield {
          if (loopScope.get(name) != None)
            throw new PFASemanticException("symbol \"%s\" may not be redeclared or shadowed".format(name), pos)

          if (!validSymbolName(name))
            throw new PFASemanticException("\"%s\" is not a valid symbol name".format(name), pos)

          val initScope = loopScope.newScope(true, true)
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, initScope, functionTable)
          calls ++= exprContext.calls

          loopScope.put(name, exprContext.retType)
          (name, exprContext.retType, exprResult)
        }

      val untilScope = loopScope.newScope(true, true)
      val (untilContext: ExpressionContext, untilResult) = until.walk(task, untilScope, functionTable)
      if (!AvroBoolean().accepts(untilContext.retType))
        throw new PFASemanticException("\"until\" predicate should be boolean, but is " + untilContext.retType, pos)
      calls ++= untilContext.calls

      val stepNameExpr: Seq[(String, TaskResult)] =
        for ((name, expr) <- step.toList) yield {
          if (loopScope.get(name) == None)
            throw new PFASemanticException("unknown symbol \"%s\" cannot be assigned with \"step\"".format(name), pos)
          else if (!loopScope.writable(name))
            throw new PFASemanticException("symbol \"%s\" belongs to a sealed enclosing scope; it cannot be modified within this block".format(name), pos)

          val stepScope = loopScope.newScope(true, true)
          val (exprContext: ExpressionContext, exprResult) = expr.walk(task, stepScope, functionTable)
          calls ++= exprContext.calls

          if (!loopScope(name).accepts(exprContext.retType))
            throw new PFASemanticException("symbol \"%s\" was declared as %s; it cannot be re-assigned as %s".format(name, loopScope(name), exprContext.retType), pos)

          (name, exprResult)
        }

      val bodyScope = loopScope.newScope(false, false)
      val bodyResults: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, bodyScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- bodyResults)
        calls ++= exprCtx.calls

      val context = For.Context(AvroNull(), calls.toSet + For.desc, bodyScope.inThisScope ++ loopScope.inThisScope, initNameTypeExpr, untilResult, bodyResults map {_._2}, stepNameExpr)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonValues = factory.objectNode
      for ((name, expr) <- init)
        jsonValues.put(name, expr.jsonNode)
      out.put("for", jsonValues)

      out.put("until", until.jsonNode)

      val jsonStep = factory.objectNode
      for ((name, expr) <- step)
        jsonStep.put(name, expr.jsonNode)
      out.put("step", jsonStep)

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)

      out
    }
  }
  object For {
    val desc = "for"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], initNameTypeExpr: Seq[(String, AvroType, TaskResult)], predicate: TaskResult, loopBody: Seq[TaskResult], stepNameExpr: Seq[(String, TaskResult)]) extends ExpressionContext
  }

  case class Foreach(name: String, array: Expression, body: Seq[Expression], seq: Boolean, pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Foreach =>
        this.name == that.name  &&  this.array == that.array  &&  this.body == that.body  &&  this.seq == that.seq  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((name, array, body, seq))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        array.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" must contain at least one statement", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()
      val loopScope = symbolTable.newScope(!seq, false)

      if (symbolTable.get(name) != None)
        throw new PFASemanticException("symbol \"%s\" may not be redeclared or shadowed".format(name), pos)

      if (!validSymbolName(name))
        throw new PFASemanticException("\"%s\" is not a valid symbol name".format(name), pos)

      val objScope = loopScope.newScope(true, true)
      val (objContext: ExpressionContext, objResult) = array.walk(task, objScope, functionTable)
      calls ++= objContext.calls

      val elementType = objContext.retType match {
        case x: AvroArray => x.items
        case _ => throw new PFASemanticException("expression referred to by \"in\" should be an array, but is " + objContext.retType, pos)
      }

      loopScope.put(name, elementType)

      val bodyScope = loopScope.newScope(false, false)
      val bodyResults: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, bodyScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- bodyResults)
        calls ++= exprCtx.calls

      val context = Foreach.Context(AvroNull(), calls.toSet + Foreach.desc, bodyScope.inThisScope ++ loopScope.inThisScope, objContext.retType, objResult, elementType, name, bodyResults map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("foreach", name)
      out.put("in", array.jsonNode)

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)

      out.put("seq", if (seq) BooleanNode.getTrue else BooleanNode.getFalse)
      out
    }
  }
  object Foreach {
    val desc = "foreach"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], objType: AvroType, objExpr: TaskResult, itemType: AvroType, name: String, loopBody: Seq[TaskResult]) extends ExpressionContext
  }

  case class Forkeyval(forkey: String, forval: String, map: Expression, body: Seq[Expression], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Forkeyval =>
        this.forkey == that.forkey  &&  this.forval == that.forval  &&  this.map == that.map  &&  this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((forkey, forval, map, body))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        map.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" must contain at least one statement", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()
      val loopScope = symbolTable.newScope(false, false)

      if (symbolTable.get(forkey) != None)
        throw new PFASemanticException("symbol \"%s\" may not be redeclared or shadowed".format(forkey), pos)
      if (symbolTable.get(forval) != None)
        throw new PFASemanticException("symbol \"%s\" may not be redeclared or shadowed".format(forval), pos)

      if (!validSymbolName(forkey))
        throw new PFASemanticException("\"%s\" is not a valid symbol name".format(forkey), pos)
      if (!validSymbolName(forval))
        throw new PFASemanticException("\"%s\" is not a valid symbol name".format(forval), pos)

      val objScope = loopScope.newScope(true, true)
      val (objContext: ExpressionContext, objResult) = map.walk(task, objScope, functionTable)
      calls ++= objContext.calls

      val elementType = objContext.retType match {
        case x: AvroMap => x.values
        case _ => throw new PFASemanticException("expression referred to by \"in\" should be a map, but is " + objContext.retType, pos)
      }

      loopScope.put(forkey, AvroString())
      loopScope.put(forval, elementType)

      val bodyScope = loopScope.newScope(false, false)
      val bodyResults: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, bodyScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- bodyResults)
        calls ++= exprCtx.calls

      val context = Forkeyval.Context(AvroNull(), calls.toSet + Forkeyval.desc, bodyScope.inThisScope ++ loopScope.inThisScope, objContext.retType, objResult, elementType, forkey, forval, bodyResults map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("forkey", forkey)
      out.put("forval", forval)
      out.put("in", map.jsonNode)

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)
      out
    }
  }
  object Forkeyval {
    val desc = "forkey-forval"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], objType: AvroType, objExpr: TaskResult, itemType: AvroType, forkey: String, forval: String, loopBody: Seq[TaskResult]) extends ExpressionContext
  }

  case class CastCase(avroPlaceholder: AvroPlaceholder, named: String, body: Seq[Expression], pos: Option[String] = None) extends Ast {
    def avroType: AvroType = avroPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: CastCase =>
        this.avroPlaceholder.toString == that.avroPlaceholder.toString  &&  this.named == that.named  &&  this.body == that.body  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((avroPlaceholder.toString, named, body))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        body.flatMap(_.collect(pf))

    if (body.size < 1)
      throw new PFASyntaxException("\"do\" block must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      if (!validSymbolName(named))
        throw new PFASemanticException("\"%s\" is not a valid symbol name".format(named), pos)

      val scope = symbolTable.newScope(false, false)
      scope.put(named, avroType)

      val results: Seq[(ExpressionContext, TaskResult)] = body.map(_.walk(task, scope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val context = CastCase.Context(results.last._1.retType, named, avroType, results.map(_._1.calls).flatten.toSet, scope.inThisScope, results map {_._2})
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("as", convertFromJson(avroPlaceholder.toString))

      out.put("named", named)

      val jsonBody = factory.arrayNode
      for (expr <- body)
        jsonBody.add(expr.jsonNode)
      out.put("do", jsonBody)

      out
    }
  }
  object CastCase {
    case class Context(retType: AvroType, name: String, toType: AvroType, calls: Set[String], symbols: Map[String, AvroType], clause: Seq[TaskResult]) extends AstContext
  }

  case class CastBlock(expr: Expression, castCases: Seq[CastCase], partial: Boolean, pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: CastBlock =>
        this.expr == that.expr  &&  this.castCases == that.castCases  &&  this.partial == that.partial  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((expr, castCases, partial))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        expr.collect(pf) ++
        castCases.flatMap(_.collect(pf))

    if (partial) {
      if (castCases.size < 1)
        throw new PFASyntaxException("\"cases\" must contain at least one case", pos)
    }
    else {
      if (castCases.size < 2)
        throw new PFASyntaxException("\"cases\" must contain at least two cases", pos)
    }

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val exprScope = symbolTable.newScope(true, true)
      val (exprContext: ExpressionContext, exprResult) = expr.walk(task, exprScope, functionTable)
      calls ++= exprContext.calls

      val exprType = exprContext.retType
      val types = castCases map {_.avroType}

      // do you have anything extraneous?
      types.find(!exprType.accepts(_)) match {
        case Some(t) => throw new PFASemanticException("\"cast\" of expression with type %s can never satisfy case %s".format(exprType, t), pos)
        case None =>
      }

      val cases = castCases.map(_.walk(task, symbolTable, functionTable))
      for ((castCtx: CastCase.Context, _) <- cases)
        calls ++= castCtx.calls

      val retType =
        if (partial)
          AvroNull()

        else {
          // are you missing anything necessary?
          val mustFindThese = exprType match {
            case x: AvroUnion => x.types
            case x => List(x)
          }

          for (mustFind <- mustFindThese)
            if (!types.exists(t => t.accepts(mustFind)  &&  mustFind.accepts(t)))
              throw new PFASemanticException("\"cast\" of expression with type %s does not contain a case for %s".format(exprType, mustFind), pos)

          try {
            P.mustBeAvro(LabelData.broadestType(cases map {case (castCtx: CastCase.Context, _) => castCtx.retType} toList))
          }
          catch {
            case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
          }
        }

      val context = CastBlock.Context(retType, calls.toSet + CastBlock.desc, exprType, exprResult, cases map {case (castCtx: CastCase.Context, castRes: TaskResult) => (castCtx, castRes)}, partial)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("cast", expr.jsonNode)

      val jsonCastCase = factory.arrayNode
      for (castCase <- castCases)
        jsonCastCase.add(castCase.jsonNode)
      out.put("cases", jsonCastCase)

      out.put("partial", if (partial) BooleanNode.getTrue else BooleanNode.getFalse)
      out
    }
  }
  object CastBlock {
    val desc = "cast-cases"
    case class Context(retType: AvroType, calls: Set[String], exprType: AvroType, expr: TaskResult, cases: Seq[(CastCase.Context, TaskResult)], partial: Boolean) extends ExpressionContext
  }

  case class Upcast(expr: Expression, avroPlaceholder: AvroPlaceholder, pos: Option[String] = None) extends Expression {
    def avroType: AvroType = avroPlaceholder.avroType

    override def equals(other: Any): Boolean = other match {
      case that: Upcast =>
        this.expr == that.expr  &&  this.avroPlaceholder.toString == that.avroPlaceholder.toString  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((expr, avroPlaceholder))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        expr.collect(pf)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val scope = symbolTable.newScope(true, true)
      val (exprContext: ExpressionContext, exprResult) = expr.walk(task, scope, functionTable)

      if (!avroType.accepts(exprContext.retType))
        throw new PFASemanticException("expression results in %s; cannot expand (\"upcast\") to %s".format(exprContext.retType, avroType), pos)

      val context = Upcast.Context(avroType, exprContext.calls + Upcast.desc, exprResult)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("upcast", expr.jsonNode)
      out.put("as", convertFromJson(avroPlaceholder.toString))
      out
    }
  }
  object Upcast {
    val desc = "upcast"
    case class Context(retType: AvroType, calls: Set[String], expr: TaskResult) extends ExpressionContext
  }

  case class IfNotNull(exprs: Map[String, Expression], thenClause: Seq[Expression], elseClause: Option[Seq[Expression]], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: IfNotNull => this.exprs == that.exprs  &&  this.thenClause == that.thenClause  &&  this.elseClause == that.elseClause  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((exprs, thenClause, elseClause))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
    exprs.values.flatMap(_.collect(pf)) ++
    thenClause.flatMap(_.collect(pf)) ++
    (if (elseClause == None) List[X]() else elseClause.get.flatMap(_.collect(pf)))

    if (exprs.size < 1)
      throw new PFASyntaxException("\"ifnotnull\" must contain at least one symbol-expression mapping", pos)

    if (thenClause.size < 1)
        throw new PFASyntaxException("\"then\" clause must contain at least one expression", pos)

    if (elseClause != None  &&  elseClause.size < 1)
      throw new PFASyntaxException("\"else\" clause must contain at least one expression", pos)

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val calls = mutable.Set[String]()

      val exprArgsScope = symbolTable.newScope(true, true)
      val assignmentScope = symbolTable.newScope(false, false)

      val symbolTypeResult: Seq[(String, AvroType, TaskResult)] = exprs map {case (name, expr) =>
        if (!validSymbolName(name))
          throw new PFASemanticException("\"%s\" is not a valid symbol name".format(name), pos)

        val (exprCtx: ExpressionContext, exprRes: TaskResult) = expr.walk(task, exprArgsScope, functionTable)

        val avroType =
          exprCtx.retType match {
            case AvroUnion(types) if (types.size > 2  &&  types.contains(AvroNull())) => AvroUnion(types filter {_ != AvroNull()})
            case AvroUnion(types) if (types.size > 1  &&  types.contains(AvroNull())) => types filter {_ != AvroNull()} head
            case x => throw new PFASemanticException("\"ifnotnull\" expressions must all be unions of something and null; case \"%s\" has type %s".format(name, x.toString), pos)
          }
        assignmentScope.put(name, avroType)

        calls ++= exprCtx.calls

        (name, avroType, exprRes)
      } toList

      val thenScope = assignmentScope.newScope(false, false)
      val thenResults: Seq[(ExpressionContext, TaskResult)] = thenClause.map(_.walk(task, thenScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      for ((exprCtx, _) <- thenResults)
        calls ++= exprCtx.calls

      val (retType, elseTaskResults, elseSymbols) =
        elseClause match {
          case Some(clause) => {
            val elseScope = symbolTable.newScope(false, false)

            val elseResults = clause.map(_.walk(task, elseScope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
            for ((exprCtx, _) <- elseResults)
              calls ++= exprCtx.calls

            val thenType = thenResults.last._1.retType
            val elseType = elseResults.last._1.retType
            val retType =
              try {
                P.mustBeAvro(LabelData.broadestType(List(thenType, elseType)))
              }
              catch {
                case err: IncompatibleTypes => throw new PFASemanticException(err.getMessage, pos)
              }

            (retType, Some(elseResults map {_._2}), Some(elseScope.inThisScope))
          }
          case None => (AvroNull(), None, None)
        }

      val context = IfNotNull.Context(retType, calls.toSet + IfNotNull.desc, symbolTypeResult, thenScope.inThisScope, thenResults map {_._2}, elseSymbols, elseTaskResults)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonExprs = factory.objectNode
      for ((name, expr) <- exprs)
        jsonExprs.put(name, expr.jsonNode)
      out.put("ifnotnull", jsonExprs)

      val jsonThenClause = factory.arrayNode
      for (expr <- thenClause)
        jsonThenClause.add(expr.jsonNode)
      out.put("then", jsonThenClause)

      elseClause match {
        case Some(clause) => {
          val jsonElseClause = factory.arrayNode
          for (expr <- clause)
            jsonElseClause.add(expr.jsonNode)
          out.put("else", jsonElseClause)
        }
        case None =>
      }

      out
    }
  }
  object IfNotNull {
    val desc = "ifnotnull"
    case class Context(retType: AvroType, calls: Set[String], symbolTypeResult: Seq[(String, AvroType, TaskResult)], thenSymbols: Map[String, AvroType], thenClause: Seq[TaskResult], elseSymbols: Option[Map[String, AvroType]], elseClause: Option[Seq[TaskResult]]) extends ExpressionContext
  }

  case class Doc(comment: String, pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Doc =>
        this.comment == that.comment  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode(Tuple1[String](comment))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = Doc.Context(AvroNull(), Set[String](Doc.desc))
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("doc", comment)
      out
    }
  }
  object Doc {
    val desc = "doc"
    case class Context(retType: AvroType, calls: Set[String]) extends ExpressionContext
  }

  case class Error(message: String, code: Option[Int], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Error =>
        this.message == that.message  &&  this.code == that.code  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((message, code))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val context = Error.Context(AvroNull(), Set[String](Error.desc), message, code)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode
      out.put("error", message)

      code match {
        case Some(number) => out.put("code", number)
        case None =>
      }
      out
    }
  }
  object Error {
    val desc = "error"
    case class Context(retType: AvroType, calls: Set[String], message: String, code: Option[Int]) extends ExpressionContext
  }

  case class Log(exprs: Seq[Expression], namespace: Option[String], pos: Option[String] = None) extends Expression {
    override def equals(other: Any): Boolean = other match {
      case that: Log =>
        this.exprs == that.exprs  &&  this.namespace == that.namespace  // but not pos
      case _ => false
    }
    override def hashCode(): Int = ScalaRunTime._hashCode((exprs, namespace))

    override def collect[X](pf: PartialFunction[Ast, X]): Seq[X] =
      super.collect(pf) ++
        exprs.flatMap(_.collect(pf))

    override def walk(task: Task, symbolTable: SymbolTable, functionTable: FunctionTable): (AstContext, TaskResult) = {
      val scope = symbolTable.newScope(true, true)
      val results: Seq[(ExpressionContext, TaskResult)] = exprs.map(_.walk(task, scope, functionTable)) collect {case (x: ExpressionContext, y: TaskResult) => (x, y)}
      val context = Log.Context(AvroNull(), results.map(_._1.calls).flatten.toSet + Log.desc, scope.inThisScope, results map { case (exprContext, taskResult) => (taskResult, exprContext.retType) }, namespace)
      (context, task(context))
    }

    override def jsonNode: JsonNode = {
      val factory = JsonNodeFactory.instance
      val out = factory.objectNode

      val jsonExprs = factory.arrayNode
      for (expr <- exprs)
        jsonExprs.add(expr.jsonNode)
      out.put("log", jsonExprs)

      namespace match {
        case Some(name) => out.put("namespace", name)
        case None =>
      }
      out
    }
  }
  object Log {
    val desc = "log"
    case class Context(retType: AvroType, calls: Set[String], symbols: Map[String, AvroType], exprTypes: Seq[(TaskResult, AvroType)], namespace: Option[String]) extends ExpressionContext
  }

}

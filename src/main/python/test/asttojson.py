#!/usr/bin/env python

import json
import unittest

from pfa.ast import *
from pfa.types import *

class TestAstToJson(unittest.TestCase):
    def testEngineConfig(self):
        self.assertEqual(
        EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [],
        {},
        None,
        {},
        {},
        None,
        None,
        None,
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "action": [{"+": [2, 2]}],
  "cells": {}, "pools": {}, "options": {}
}'''))

        self.assertEqual(
        EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {},
        None,
        {},
        {},
        None,
        None,
        None,
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "cells": {}, "pools": {}, "options": {}
}'''))

        self.assertEqual(
        EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {},
        {},
        None,
        None,
        None,
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells": {}, "pools": {}, "options": {}
}'''))

        self.assertEqual(
        EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {"private": Cell(AvroInt(), "0", False)},
        {},
        None,
        None,
        None,
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools": {}, "options": {}
}'''))

        self.assertEqual(EngineConfig(
        "test",
         Method.MAP,
         AvroInt(),
         AvroString(),
         [Call("+", [LiteralInt(2), LiteralInt(2)])],
         [Call("+", [LiteralInt(2), LiteralInt(2)])],
         [Call("+", [LiteralInt(2), LiteralInt(2)])],
         {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
         None,
         {"private": Cell(AvroInt(), "0", False)},
         {"private": Pool(AvroInt(), {}, False)},
         None,
         None,
         None,
         {}).jsonNode,
         json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools":{"private":{"type":"int","init":{},"shared":false}},
  "options": {}
}'''))

        self.assertEqual(EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {"private": Cell(AvroInt(), "0", False)},
        {"private": Pool(AvroInt(), {}, False)},
        12345,
        None,
        None,
        {}).jsonNode,
    json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools":{"private":{"type":"int","init":{},"shared":false}},
  "randseed":12345,
  "options": {}
}'''))

        self.assertEqual(EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {"private": Cell(AvroInt(), "0", False)},
        {"private": Pool(AvroInt(), {}, False)},
        12345,
        "hello",
        None,
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools":{"private":{"type":"int","init":{},"shared":false}},
  "randseed":12345,
  "doc":"hello",
  "options": {}
}'''))

        self.assertEqual(EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {"private": Cell(AvroInt(), "0", False)},
        {"private": Pool(AvroInt(), {}, False)},
        12345,
        "hello",
        json.loads('''{"internal": "data"}'''),
        {}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools":{"private":{"type":"int","init":{},"shared":false}},
  "randseed":12345,
  "doc":"hello",
  "metadata":{"internal":"data"},
  "options": {}
}'''))

        self.assertEqual(EngineConfig(
        "test",
        Method.MAP,
        AvroInt(),
        AvroString(),
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        [Call("+", [LiteralInt(2), LiteralInt(2)])],
        {"f": FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])},
        None,
        {"private": Cell(AvroInt(), "0", False)},
        {"private": Pool(AvroInt(), {}, False)},
        12345,
        "hello",
        json.loads('''{"internal": "data"}'''),
        {"param": json.loads("3")}).jsonNode,
        json.loads('''{
  "name": "test",
  "method": "map",
  "input": "int",
  "output": "string",
  "begin": [{"+": [2, 2]}],
  "action": [{"+": [2, 2]}],
  "end": [{"+": [2, 2]}],
  "fcns": {"f": {"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}},
  "cells":{"private":{"type":"int","init":0,"shared":false}},
  "pools":{"private":{"type":"int","init":{},"shared":false}},
  "randseed":12345,
  "doc":"hello",
  "metadata":{"internal":"data"},
  "options":{"param":3}
}'''))

    def testCell(self):
        self.assertEqual(Cell(AvroInt(), "0", False).jsonNode, json.loads('''{"type":"int","init":0,"shared":false}'''))

    def testPool(self):
        self.assertEqual(Pool(AvroInt(), {}, False).jsonNode, json.loads('''{"type":"int","init":{},"shared":false}'''))
        self.assertEqual(Pool(AvroInt(), {"one": "1"}, False).jsonNode, json.loads('''{"type":"int","init":{"one":1},"shared":false}'''))

    def testDefineFunction(self):
        self.assertEqual(FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()]).jsonNode, json.loads('''{"params":[{"x":"int"},{"y":"string"}],"ret":"null","do":[null]}'''))

    def testCallFunction(self):
        self.assertEqual(Call("+", [LiteralInt(2), LiteralInt(2)]).jsonNode, json.loads('''{"+":[2,2]}'''))

    def testCallWithFcn(self):
        self.assertEqual(Call("sort", [Ref("array"), FcnRef("byname")]).jsonNode, json.loads('''{"sort":["array",{"fcnref": "byname"}]}'''))

    def testCallWithFcnDef(self):
        self.assertEqual(Call("sort", [Ref("array"), FcnDef([{"x": AvroInt()}, {"y": AvroString()}], AvroNull(), [LiteralNull()])]).jsonNode,
                         json.loads('''{"sort":["array",{"params": [{"x": "int"}, {"y": "string"}], "ret": "null", "do": [null]}]}'''))

    def testRef(self):
        self.assertEqual(Ref("x").jsonNode, "x")

    def testNull(self):
        self.assertEqual(LiteralNull().jsonNode, json.loads("null"))

    def testBoolean(self):
        self.assertEqual(LiteralBoolean(True).jsonNode, json.loads("true"))
        self.assertEqual(LiteralBoolean(False).jsonNode, json.loads("false"))

    def testInt(self):
        self.assertEqual(LiteralInt(2).jsonNode, json.loads('''2'''))

    def testLong(self):
        self.assertEqual(LiteralLong(2).jsonNode, json.loads('''{"long":2}'''))

    def testFloat(self):
        self.assertEqual(LiteralFloat(2.5).jsonNode, json.loads('''{"float":2.5}'''))

    def testDouble(self):
        self.assertEqual(LiteralDouble(2.2).jsonNode, json.loads("2.2"))

    def testString(self):
        self.assertEqual(LiteralString("hello").jsonNode, json.loads('''{"string":"hello"}'''))

    def testBase64(self):
        self.assertEqual(LiteralBase64("hello".encode("utf-8")).jsonNode, json.loads('''{"base64":"aGVsbG8="}'''))

    def testLiteral(self):
        self.assertEqual(Literal(AvroRecord([AvroField("one", AvroInt()), AvroField("two", AvroDouble()), AvroField("three", AvroString())], "SimpleRecord"), '''{"one": 1, "two": 2.2, "three": "THREE"}''').jsonNode,
        json.loads('''{"type":{"type":"record","name":"SimpleRecord","fields":[{"name":"one","type":"int"},{"name":"two","type":"double"},{"name":"three","type":"string"}]},"value":{"one":1,"two":2.2,"three":"THREE"}}'''))

    def testNewRecord(self):
        self.assertEqual(NewObject({"one": LiteralInt(1), "two": LiteralDouble(2.2), "three": LiteralString("THREE")},
                                   AvroRecord([AvroField("one", AvroInt()), AvroField("two", AvroDouble()), AvroField("three", AvroString())], "SimpleRecord"), AvroTypeBuilder()).jsonNode,
        json.loads('''{"new":{"one":1,"two":2.2,"three":{"string":"THREE"}},"type":{"type":"record","name":"SimpleRecord","fields":[{"name":"one","type":"int"},{"name":"two","type":"double"},{"name":"three","type":"string"}]}}'''))

    def testNewArray(self):
        self.assertEqual(NewArray([LiteralInt(1), LiteralInt(2), LiteralInt(3)], AvroArray(AvroInt()), AvroTypeBuilder()).jsonNode, json.loads('''{"new":[1,2,3],"type":{"type":"array","items":"int"}}'''))

    def testDo(self):
        self.assertEqual(Do([Ref("x"), Ref("y"), Ref("z")]).jsonNode, json.loads('''{"do":["x","y","z"]}'''))

    def testLet(self):
        self.assertEqual(Let({"x": LiteralInt(3), "y": LiteralInt(4)}).jsonNode, json.loads('''{"let":{"x":3,"y":4}}'''))

    def testSet(self):
        self.assertEqual(SetVar({"x": LiteralInt(3), "y": LiteralInt(4)}).jsonNode, json.loads('''{"set":{"x":3,"y":4}}'''))

    def testAttrGet(self):
        self.assertEqual(AttrGet("a", [Ref("a"), LiteralInt(1), LiteralString("b")]).jsonNode, json.loads('''{"attr":"a","path":["a",1,{"string":"b"}]}'''))

    def testAttrSet(self):
        self.assertEqual(AttrTo("a", [Ref("a"), LiteralInt(1), LiteralString("b")], LiteralDouble(2.2)).jsonNode, json.loads('''{"attr":"a","path":["a",1,{"string":"b"}],"to":2.2}'''))

    def testCellGet(self):
        self.assertEqual(CellGet("c", []).jsonNode, json.loads('''{"cell":"c"}'''))
        self.assertEqual(CellGet("c", [Ref("a"), LiteralInt(1), LiteralString("b")]).jsonNode, json.loads('''{"cell":"c","path":["a",1,{"string":"b"}]}'''))

    def testCellSet(self):
        self.assertEqual(CellTo("c", [], LiteralDouble(2.2)).jsonNode, json.loads('''{"cell":"c","to":2.2}'''))
        self.assertEqual(CellTo("c", [Ref("a"), LiteralInt(1), LiteralString("b")], LiteralDouble(2.2)).jsonNode, json.loads('''{"cell":"c","path":["a",1,{"string":"b"}],"to":2.2}'''))

    def testPoolGet(self):
        self.assertEqual(PoolGet("p", [Ref("a"), LiteralInt(1), LiteralString("b")]).jsonNode, json.loads('''{"pool":"p","path":["a",1,{"string":"b"}]}'''))

    def testPoolSet(self):
        self.assertEqual(PoolTo("p", [Ref("a"), LiteralInt(1), LiteralString("b")], LiteralDouble(2.2), None).jsonNode, json.loads('''{"pool":"p","path":["a",1,{"string":"b"}],"to":2.2}'''))
        self.assertEqual(PoolTo("p", [Ref("a"), LiteralInt(1), LiteralString("b")], LiteralDouble(2.2), LiteralDouble(2.2)).jsonNode, json.loads('''{"pool":"p","path":["a",1,{"string":"b"}],"to":2.2,"init":2.2}'''))

    def testIf(self):
        self.assertEqual(If(LiteralBoolean(True), [Ref("x")], None).jsonNode, json.loads('''{"if":true,"then":["x"]}'''))
        self.assertEqual(If(LiteralBoolean(True), [Ref("x")], [Ref("y")]).jsonNode, json.loads('''{"if":true,"then":["x"],"else":["y"]}'''))

    def testCond(self):
        self.assertEqual(Cond([If(LiteralBoolean(False), [Ref("x")], None), If(LiteralBoolean(True), [Ref("y")], None)], None).jsonNode,
                         json.loads('''{"cond":[{"if":false,"then":["x"]},{"if":true,"then":["y"]}]}'''))
        self.assertEqual(Cond([If(LiteralBoolean(False), [Ref("x")], None), If(LiteralBoolean(True), [Ref("y")], None)], [Ref("z")]).jsonNode,
                         json.loads('''{"cond":[{"if":false,"then":["x"]},{"if":true,"then":["y"]}],"else":["z"]}'''))

    def testWhile(self):
        self.assertEqual(While(LiteralBoolean(True), [Call("+", [LiteralInt(2), LiteralInt(2)])]).jsonNode,
                         json.loads('''{"while":true,"do":[{"+":[2,2]}]}'''))

    def testDoUntil(self):
        self.assertEqual(DoUntil([Call("+", [LiteralInt(2), LiteralInt(2)])], LiteralBoolean(True)).jsonNode,
                         json.loads('''{"do":[{"+":[2,2]}],"until":true}'''))

    def testFor(self):
        self.assertEqual(For({"i": LiteralInt(0)}, Call("<", [Ref("i"), LiteralInt(10)]), {"i": Call("+", [Ref("i"), LiteralInt(1)])}, [Ref("i")], False).jsonNode,
                         json.loads('''{"for":{"i":0},"until":{"<":["i",10]},"step":{"i":{"+":["i",1]}},"do":["i"],"seq":false}'''))

    def testForeach(self):
        self.assertEqual(Foreach("x", Literal(AvroArray(AvroInt()), '''[1, 2, 3]'''), [Ref("x")], False).jsonNode,
                         json.loads('''{"foreach":"x","in":{"type":{"type":"array","items":"int"},"value":[1,2,3]},"do":["x"],"seq":false}'''))

    def testForkeyval(self):
        self.assertEqual(Forkeyval("k", "v", Literal(AvroMap(AvroInt()), '''{"one": 1, "two": 2, "three": 3}'''), [Ref("k")]).jsonNode,
                         json.loads('''{"forkey":"k","forval":"v","in":{"type":{"type":"map","values":"int"},"value":{"one":1,"two":2,"three":3}},"do":["k"]}'''))

    def testCast(self):
        self.assertEqual(CastBlock(LiteralInt(3), [CastCase(AvroString(), "x", [Ref("x")]), CastCase(AvroInt(), "x", [Ref("x")])], False).jsonNode,
                         json.loads('''{"cast":3,"cases":[{"as":"string","named":"x","do":["x"]},{"as":"int","named":"x","do":["x"]}],"partial":false}'''))

    def testDoc(self):
        self.assertEqual(Doc("hello").jsonNode, json.loads('''{"doc":"hello"}'''))

    def testError(self):
        self.assertEqual(Error("hello", None).jsonNode, json.loads('''{"error":"hello"}'''))
        self.assertEqual(Error("hello", 3).jsonNode, json.loads('''{"error":"hello","code":3}'''))

    def testLog(self):
        self.assertEqual(Log([LiteralString("hello")], None).jsonNode, json.loads('''{"log":[{"string":"hello"}]}'''))
        self.assertEqual(Log([LiteralString("hello")], "DEBUG").jsonNode, json.loads('''{"log":[{"string":"hello"}],"namespace":"DEBUG"}'''))
        self.assertEqual(Log([Call("+", [LiteralInt(2), LiteralInt(2)])], None).jsonNode, json.loads('''{"log":[{"+":[2,2]}]}'''))

if __name__ == "__main__":
    unittest.main()

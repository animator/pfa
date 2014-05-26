#!/usr/bin/env python

import json
import unittest

from pfa.reader import yamlToAst
from pfa.genpy import PFAEngine
from pfa.errors import *

class TestGeneratePython(unittest.TestCase):
    def testLiteralNull(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action: null
''')
        self.assertEqual(engine.action(None), None)

    def testLiteralBoolean(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: boolean
action: true
''')
        self.assertEqual(engine.action(None), True)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: boolean
action: false
''')
        self.assertEqual(engine.action(None), False)

    def testLiteralInt(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action: 12
''')
        self.assertEqual(engine.action(None), 12)
        self.assertIsInstance(engine.action(None), int)

    def testLiteralLong(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: long
action: {long: 12}
''')
        self.assertEqual(engine.action(None), 12)
        self.assertIsInstance(engine.action(None), int)

    def testLiteralFloat(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: float
action: {float: 12}
''')
        self.assertEqual(engine.action(None), 12.0)
        self.assertIsInstance(engine.action(None), float)

    def testLiteralDouble(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: double
action: 12.4
''')
        self.assertEqual(engine.action(None), 12.4)
        self.assertIsInstance(engine.action(None), float)

    def testLiteralString(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action: [["hello world"]]
''')
        self.assertEqual(engine.action(None), "hello world")
        self.assertIsInstance(engine.action(None), basestring)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action: {string: "hello world"}
''')
        self.assertEqual(engine.action(None), "hello world")
        self.assertIsInstance(engine.action(None), basestring)

    def testLiteralBase64(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: bytes
action: {base64: "aGVsbG8="}
''')
        self.assertEqual(engine.action(None), "hello")
        self.assertIsInstance(engine.action(None), basestring)

    def testComplexLiterals(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: {type: array, items: string}
action:
  type: {type: array, items: string}
  value: [one, two, three]
''')
        self.assertEqual(engine.action(None), ["one", "two", "three"])

    def testNewRecord(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
action:
  type: SimpleRecord
  new: {one: 1, two: 2.2, three: ["THREE"]}
''')
        self.assertEqual(engine.action(None), {"one": 1, "two": 2.2, "three": "THREE"})

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
action:
  type: SimpleRecord
  new: {one: {long: 1}, two: 2.2, three: ["THREE"]}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
action:
  type: SimpleRecord
  new: {one: 1, two: 2.2, three: ["THREE"], four: 4.4}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
action:
  type: SimpleRecord
  new: {one: 1, two: 2.2}
'''))

    def testRecordWithInlineTypes(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: SimpleRecord
action:
  type: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
  new: {one: 1, two: 2.2, three: ["THREE"]}
''')
        self.assertEqual(engine.action(None), {"one": 1, "two": 2.2, "three": "THREE"})

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: SimpleRecord
action:
  type: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
  new: {one: {long: 1}, two: 2.2, three: ["THREE"]}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: SimpleRecord
action:
  type: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
  new: {one: 1, two: 2.2, three: ["THREE"], four: 4.4}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: SimpleRecord
action:
  type: {type: record, name: SimpleRecord, fields: [{name: one, type: int}, {name: two, type: double}, {name: three, type: string}]}
  new: {one: 1, two: 2.2}
'''))

    def testNewMap(self):
        engine, = PFAEngine.fromYaml('''
input: int
output: {type: map, values: int}
action:
  type: {type: map, values: int}
  new: {one: 1, two: 2, three: input}
''')
        self.assertEqual(engine.action(5), {"one": 1, "two": 2, "three": 5})

    def testNewArray(self):
        engine, = PFAEngine.fromYaml('''
input: int
output: {type: array, items: int}
action:
  type: {type: array, items: int}
  new: [1, 2, input]
''')
        self.assertEqual(engine.action(5), [1, 2, 5])

    def collectLogs(self, engine):
        out = []
        def logger(message, namespace):
            out.append((namespace, message))

        engine.logger = logger
        engine.action(None)
        return out

    def testWriteToLogs(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  log: [{string: "hello"}]
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["hello"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  log: [1, 2, 3]
''')
        self.assertEqual(self.collectLogs(engine), [(None, [1, 2, 3])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  log: [[hello]]
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["hello"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  log: [[hello]]
  namespace: filter-me
''')
        self.assertEqual(self.collectLogs(engine), [("filter-me", ["hello"])])

    def testVariableDeclarations(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - let: {x: [hello]}
  - x
''')
        self.assertEqual(engine.action(None), "hello")

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - x
  - let: {x: [hello]}
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - let: {x: [hello], y: 12}
  - y
''')
        self.assertEqual(engine.action(None), 12)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - let: {x: [hello], y: x}
  - x
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - let: {x: {let: {y: [stuff]}}}
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - let:
      x:
        do:
          - {let: {y: [stuff]}}
          - y
  - x
''')
        self.assertEqual(engine.action(None), "stuff")

    def testVariableReassignment(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - let: {x: [hello]}
  - set: {x: [there]}
  - x
''')
        self.assertEqual(engine.action(None), "there")

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - set: {x: [there]}
  - let: {x: [hello]}
  - x
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - let: {x: [hello]}
  - set: {x: 12}
  - x
'''))

    def testCallFunctions(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action: {+: [2, 2]}
''')
        self.assertEqual(engine.action(None), 4)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action: {+: [2, [hello]]}
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action: {+: [{+: [2, 2]}, 2]}
''')
        self.assertEqual(engine.action(None), 6)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action: {+: [{let: {x: 5}}, 2]}
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  +:
    - do: [{let: {x: 5}}, x]
    - 2
''')
        self.assertEqual(engine.action(None), 7)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - let: {x: 5}
  - {+: [x, 2]}
''')
        self.assertEqual(engine.action(None), 7)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 5}}
  - "+":
      - {do: [{let: {x: 5}}, x]}
      - 2
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 5}}
  - "+":
      - {do: [{set: {x: 10}}, x]}
      - 2
'''))

    def testIfExpressions(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: true
  then: 3
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: [hello]
  then: 3
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - if: true
    then:
      - {set: {x: 99}}
  - x
''')
        self.assertEqual(engine.action(None), 99)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - if: true
    then:
      - {let: {x: 99}}
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - if: true
    then:
      - {set: {x: 99}}
    else:
      - {set: {x: 55}}
  - x
''')
        self.assertEqual(engine.action(None), 99)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - if: false
    then:
      - {set: {x: 99}}
    else:
      - {set: {x: 55}}
  - x
''')
        self.assertEqual(engine.action(None), 55)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: true
  then:
    - 20
  else:
    - 30
''')
        self.assertEqual(engine.action(None), 20)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: false
  then:
    - 20
  else:
    - 30
''')
        self.assertEqual(engine.action(None), 30)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [string, int]
action:
  if: false
  then:
    - 20
  else:
    - [string]
''')
        self.assertEqual(engine.action(None), "string")

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: true
  then:
    - {let: {x: 999}}
    - x
  else:
    - 50
''')
        self.assertEqual(engine.action(None), 999)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: true
  then:
    - {let: {x: 999}}
    - x
  else:
    - x
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: true
  then:
    - x
  else:
    - {let: {x: 999}}
    - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: {"!=": [2, 3]}
  then:
    - {+: [5, 5]}
  else:
    - {+: [123, 456]}
''')
        self.assertEqual(engine.action(None), 10)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  "+":
    - if: true
      then: [5]
      else: [2]
    - 100
''')
        self.assertEqual(engine.action(None), 105)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {log: [{string: "one"}]}
  - if: true
    then: [{log: [{string: "two"}]}]
    else: [{log: [{string: "ARG!"}]}]
  - if: false
    then: [{log: [{string: "ARGY-ARG-ARG!"}]}]
    else: [{log: [{string: "three"}]}]
  - if: true
    then: [{log: [{string: "four"}]}]
  - if: false
    then: [{log: [{string: "AAAAAAAAARG!"}]}]
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["one"]), (None, ["two"]), (None, ["three"]), (None, ["four"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: true
  then:
    - if: true
      then:
        - if: true
          then:
            - {log: [{string: "HERE"}]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["HERE"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: true
  then:
    - if: true
      then:
        - if: true
          then:
            - {log: [{string: "HERE"}]}
          else:
            - {log: [{string: "AAAARG!"}]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["HERE"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: true
  then:
    - if: true
      then:
        - if: true
          then:
            - {log: [{string: "HERE"}]}
      else:
        - {log: [{string: "BOO!"}]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["HERE"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - if: true
    then:
      - {let: {x: 99}}
    else:
      - {let: {x: 99}}
  - 123
''')
        self.assertEqual(engine.action(None), 123)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  if: true
  then: [1]
  else: [2]
''')
        self.assertEqual(engine.action(None), 1)

    def testCondExpressions(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: false, then: [1]}
    - {if: true, then: [2]}
    - {if: true, then: [3]}
  else: [4]
''')
        self.assertEqual(engine.action(None), 2)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: false, then: [1]}
    - {if: false, then: [2]}
    - {if: false, then: [3]}
  else: [4]
''')
        self.assertEqual(engine.action(None), 4)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  cond:
    - {if: false, then: [1]}
    - {if: false, then: [2]}
    - {if: false, then: [3]}
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: false, then: [{let: {x: 5}}, 1]}
    - {if: false, then: [{let: {x: 5}}, 2]}
    - {if: false, then: [{let: {x: 5}}, 3]}
  else: [{let: {x: 5}}, 4]
''')
        self.assertEqual(engine.action(None), 4)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  cond:
    - {if: false, then: [{let: {x: 5}}, 1]}
    - {if: false, then: [{let: {x: 5}}, 2]}
    - {if: false, then: [{let: {x: 5}}, 3]}
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: false, then: [{let: {x: 5}}, 1]}
    - {if: false, then: [{let: {x: 5}}, 2]}
    - {if: false, then: [{let: {x: 5}}, 3]}
  else: [{set: {x: 5}}, 4]
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: false, then: [{let: {x: 5}}, 1]}
    - {if: false, then: [{set: {x: 5}}, 2]}
    - {if: false, then: [{set: {x: 5}}, 3]}
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: {do: [{let: {x: 5}}, false]}, then: [{let: {x: 5}}, 1]}
    - {if: {do: [{let: {x: 5}}, false]}, then: [{let: {x: 5}}, 2]}
    - {if: {do: [{let: {x: 5}}, false]}, then: [{let: {x: 5}}, 3]}
  else: [{let: {x: 5}}, 4]
''')
        self.assertEqual(engine.action(None), 4)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  cond:
    - {if: {do: [{let: {x: 5}}, true]}, then: [{let: {x: 5}}, 1]}
    - {if: {do: [{let: {x: 5}}, false]}, then: [{let: {x: 5}}, 2]}
    - {if: {do: [{let: {x: 5}}, false]}, then: [{let: {x: 5}}, 3]}
  else: [{let: {x: 5}}, 4]
''')
        self.assertEqual(engine.action(None), 1)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - cond:
      - {if: {do: [{let: {x: 5}}, true]}, then: [1]}
      - {if: {do: [{let: {x: 5}}, false]}, then: [2]}
      - {if: {do: [{let: {x: 5}}, false]}, then: [3]}
    else: [{let: {x: 5}}, 4]
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 3}}
  - cond:
      - {if: {do: [{set: {x: 1}}, true]}, then: [1]}
      - {if: {do: [{set: {x: 2}}, false]}, then: [2]}
      - {if: {do: [{set: {x: 3}}, false]}, then: [3]}
    else: [4]
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [string, int, double]
action:
  cond:
    - {if: false, then: [1]}
    - {if: true, then: [[two]]}
    - {if: true, then: [3.0]}
  else: [4]
''')
        self.assertEqual(engine.action(None), "two")

    def testWhileLoops(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: 0}}
  - while: {"!=": [x, 5]}
    do:
      - {log: [x]}
      - {set: {x: {+: [x, 1]}}}
''')
        self.assertEqual(self.collectLogs(engine), [(None, [0]), (None, [1]), (None, [2]), (None, [3]), (None, [4])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {"!=": [x, 5]}
    do:
      - {set: {x: {+: [x, 1]}}}
      - x
  - x
''')
        self.assertEqual(engine.action(None), 5)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {+: [2, 2]}
    do:
      - {log: [x]}
      - {set: {x: {+: [x, 1]}}}
      - x
  - x
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {let: {y: 12}}
    do:
      - {log: [x]}
      - {set: {x: {+: [x, 1]}}}
      - x
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {do: [{+: [2, 2]}, {"!=": [x, 5]}]}
    do:
      - {set: {x: {+: [x, 1]}}}
      - x
  - x
''')
        self.assertEqual(engine.action(None), 5)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - {let: {y: 0}}
  - while: {do: [{set: {y: 5}}, {"!=": [x, y]}]}
    do:
      - {set: {x: {+: [x, 1]}}}
      - x
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {"!=": [x, 5]}
    do:
      - {set: {x: {+: [x, 1]}}}
  - x
''')
        self.assertEqual(engine.action(None), 5)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - while: {"!=": [y, 5]}
    do:
      - {let: {y: {+: [x, 1]}}}
      - {set: {x: y}}
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [before], x: 0}}
  - while: {"!=": [x, 0]}
    do:
      - {set: {y: [after]}}
  - y
''')
        self.assertEqual(engine.action(None), "before")

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: 0}}
  - do:
      - {log: [x]}
      - {set: {x: {+: [x, 1]}}}
    until: {==: [x, 5]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, [0]), (None, [1]), (None, [2]), (None, [3]), (None, [4])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: 0}}
  - do:
      - {set: {x: {+: [x, 1]}}}
      - x
    until: {==: [x, 5]}
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - do:
      - {set: {x: {+: [x, 1]}}}
      - x
    until: {==: [x, 5]}
  - x
''')
        self.assertEqual(engine.action(None), 5)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - do:
      - {set: {x: {+: [x, 1]}}}
    until: {==: [x, 5]}
  - x
''')
        self.assertEqual(engine.action(None), 5)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - do:
      - {let: {y: {+: [x, 1]}}}
      - {set: {x: y}}
    until: {==: [y, 5]}
  - x
''')
        self.assertEqual(engine.action(None), 5)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - do:
      - {let: {y: {+: [x, 1]}}}
      - {set: {x: y}}
    until: {==: [y, 5]}
  - y
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [before], x: 0}}
  - do:
      - {set: {y: [after]}}
    until: {==: [x, 0]}
  - y
''')
        self.assertEqual(engine.action(None), "after")

    def testForLoops(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: 0}
  while: {"!=": [x, 5]}
  step: {x: {+: [x, 1]}}
  do:
    - {log: [x]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, [0]), (None, [1]), (None, [2]), (None, [3]), (None, [4])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - for: {x: 0}
    while: {"!=": [x, 5]}
    step: {x: {+: [x, 1]}}
    do:
      - x
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - for: {x: 0}
    while: {"!=": [x, 5]}
    step: {x: {+: [x, 1]}}
    do:
      - x
  - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - for: {dummy: null}
    while: {"!=": [x, 5]}
    step: {x: {+: [x, 1]}}
    do:
      - x
  - x
''')
        self.assertEqual(engine.action(None), 5)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: {+: [99, -99]}}
  while: {"!=": [x, {+: [2, 3]}]}
  step: {x: {+: [x, {-: [3, 2]}]}}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: {let: {y: 0}}}
  while: {"!=": [x, {+: [2, 3]}]}
  step: {x: {+: [x, {-: [3, 2]}]}}
  do:
    - x
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: {do: [{let: {y: 0}}, y]}}
  while: {"!=": [x, {+: [2, 3]}]}
  step: {x: {+: [x, {-: [3, 2]}]}}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: 0}
  while: {"!=": [x, 5]}
  step: {x: {+: [x, 1]}}
  do:
    - {let: {y: x}}
    - y
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  for: {x: 0}
  while: {"!=": [x, 5]}
  step: {x: {+: [y, 1]}}
  do:
    - {let: {y: x}}
    - y
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  for: {x: 0}
  while: {"!=": [y, 5]}
  step: {x: {+: [x, 1]}}
  do:
    - {let: {y: x}}
    - y
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  for: {x: 0}
  while: {"!=": [x, 0]}
  step: {x: {+: [x, 1]}}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [before]}}
  - for: {x: 0}
    while: {"!=": [x, 0]}
    step: {x: {+: [x, 1]}}
    do:
      - {set: {y: [after]}}
  - y
''')
        self.assertEqual(engine.action(None), "before")

    def testForeachLoops(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  foreach: x
  in: {type: {type: array, items: string}, value: [one, two, three]}
  do:
    - {log: [x]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, ["one"]), (None, ["two"]), (None, ["three"])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  foreach: x
  in: {type: {type: array, items: string}, value: [one, two, three]}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  foreach: x
  in: {type: {type: array, items: string}, value: [one, two, three]}
  do:
    - {let: {y: x}}
    - y
''')
        self.assertEqual(engine.action(None), None)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {y: [zero]}}
  - foreach: x
    in: {type: {type: array, items: string}, value: [one, two, three]}
    do:
      - {set: {y: x}}
      - y
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {y: [zero]}}
  - foreach: x
    in: {type: {type: array, items: string}, value: [one, two, three]}
    do:
      - {set: {y: x}}
      - y
    seq: true
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [zero]}}
  - foreach: x
    in: {type: {type: array, items: string}, value: [one, two, three]}
    do:
      - {set: {y: x}}
    seq: true
  - y
''')
        self.assertEqual(engine.action(None), "three")

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {y: [zero], array: {type: {type: array, items: string}, value: [one, two, three]}}}
  - foreach: x
    in: array
    do:
      - {set: {y: x}}
  - y
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [zero], array: {type: {type: array, items: string}, value: [one, two, three]}}}
  - foreach: x
    in: array
    do:
      - {set: {y: x}}
    seq: true
  - y
''')
        self.assertEqual(engine.action(None), "three")

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {y: [zero], array: {type: {type: array, items: string}, value: [one, two, three]}}}
  - foreach: x
    in: array
    do:
      - {set: {array: {type: {type: array, items: string}, value: [zero]}}}
      - {set: {y: x}}
    seq: true
  - y
''')
        self.assertEqual(engine.action(None), "three")

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  foreach: x
  in: {type: {type: array, items: string}, value: []}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  foreach: x
  in: {type: {type: array, items: double}, value: [1, 2, 3]}
  do:
    - x
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: double
action:
  - {let: {y: 0.0}}
  - foreach: x
    in: {type: {type: array, items: double}, value: [1, 2, 3]}
    do:
      - {set: {y: x}}
    seq: true
  - y
''')
        self.assertEqual(engine.action(None), 3.0)

    def testForkeyvalLoops(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  forkey: n
  forval: v
  in: {type: {type: map, values: int}, value: {one: 1, two: 2, three: 3}}
  do:
    - {log: [n, v]}
''')
        self.assertEqual(sorted(self.collectLogs(engine)), sorted([(None, ["one", 1]), (None, ["two", 2]), (None, ["three", 3])]))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: 0}}
  - forkey: n
    forval: v
    in: {type: {type: map, values: int}, value: {one: 1, two: 2, three: 3}}
    do:
      - {set: {x: v}}
  - x
''')
        engine.action(None)

    def testTypeCastBlocks(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{log: [x, y]}]}
      - {as: "double", named: y, do: [{log: [x, y]}]}
      - {as: "string", named: y, do: [{log: [x, y]}]}
''')
        self.assertEqual(self.collectLogs(engine), [(None, [{"double": 2.2}, 2.2])])

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{log: [x, y]}]}
      - {as: "double", named: y, do: [{log: [x, y]}]}
      - {as: "string", named: y, do: [{log: [x, y]}]}
      - {as: "bytes", named: y, do: [{log: [x, y]}]}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{log: [x, y]}]}
      - {as: "double", named: y, do: [{log: [x, y]}]}
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{log: [x, y]}]}
      - {as: "double", named: y, do: [{log: [x, y]}]}
    partial: true
''')
        self.assertEqual(self.collectLogs(engine), [(None, [{"double": 2.2}, 2.2])])

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [double, string]
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - {let: {z: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{set: {z: y}}]}
      - {as: "double", named: y, do: [{set: {z: y}}]}
      - {as: "string", named: y, do: [{set: {z: y}}]}
  - z
''')
        self.assertEqual(engine.action(None), 2.2)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [double, string]
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - {let: {z: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: [{set: {z: y}}]}
      - {as: "double", named: y, do: [{set: {z: y}}]}
      - {as: "string", named: y, do: [{set: {z: y}}]}
    partial: true
  - z
''')
        self.assertEqual(engine.action(None), 2.2)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: double
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: 888.88}
      - {as: "double", named: y, do: [y]}
      - {as: "string", named: y, do: 999.99}
''')
        self.assertEqual(engine.action(None), 2.2)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  - {let: {x: {type: ["int", "double", "string"], value: {"double": 2.2}}}}
  - cast: x
    cases:
      - {as: "int", named: y, do: 888.88}
      - {as: "double", named: y, do: [y]}
      - {as: "string", named: y, do: 999.99}
    partial: true
''')
        self.assertEqual(engine.action(None), None)

    def testUpcast(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: double
action: {upcast: 3, as: "double"}
''')
        self.assertEqual(engine.action(None), 3.0)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [double, string]
action: {upcast: 3, as: ["double", "string"]}
''')
        self.assertEqual(engine.action(None), 3.0)

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: double
action:
  - {let: {fred: {upcast: 3, as: "double"}}}
  - {set: {fred: [hello]}}
  - fred
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: "null"
output: string
action:
  - {let: {fred: {upcast: 3, as: "double"}}}
  - {set: {fred: [hello]}}
  - fred
'''))

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [double, string]
action:
  - {let: {fred: {upcast: 3, as: ["double", "string"]}}}
  - {set: {fred: [hello]}}
  - fred
''')
        self.assertEqual(engine.action(None), "hello")

    def testIfNotNull(self):
        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: double
action:
  ifnotnull: {x: input}
  then: x
  else: 12
''')
        self.assertEqual(engine.action(5.0), 5.0)
        self.assertEqual(engine.action(None), 12.0)

        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: double
action:
  ifnotnull: {x: input, y: input}
  then: {+: [x, y]}
  else: 12
''')
        self.assertEqual(engine.action(5.0), 10.0)
        self.assertEqual(engine.action(None), 12.0)

        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: "null"
action:
  ifnotnull: {x: input, y: input}
  then: {+: [x, y]}
''')
        self.assertEqual(engine.action(5.0), None)
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: double
action:
  - let: {z: -3.0}
  - ifnotnull: {x: input, y: input}
    then: {set: {z: {+: [x, y]}}}
  - z
''')
        self.assertEqual(engine.action(5.0), 10.0)
        self.assertEqual(engine.action(None), -3.0)

        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: double
action:
  - let: {z: -3.0}
  - ifnotnull: {x: input, y: input}
    then: {set: {z: {+: [x, y]}}}
    else: {set: {z: 999.9}}
  - z
''')
        self.assertEqual(engine.action(5.0), 10.0)
        self.assertEqual(engine.action(None), 999.9)

        engine, = PFAEngine.fromYaml('''
input: [double, "null"]
output: [double, string]
action:
  - ifnotnull: {x: input}
    then: x
    else: [[whatever]]
''')
        self.assertEqual(engine.action(5.0), 5.0)
        self.assertEqual(engine.action("hello"), "hello")
        self.assertEqual(engine.action(None), "whatever")

    def testDoc(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: [int, "null"]
action:
  if: true
  then:
    - {doc: "This is very nice"}
  else:
    - {+: [5, 5]}
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: [int, "null"]
action:
  if: true
  then:
    - {doc: "This is very nice"}
    - if: true
      then:
        - {+: [5, 5]}
''')
        self.assertEqual(engine.action(None), None)

    def testError(self):
        def callme():
            engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action: {error: "This is bad"}
''')
            engine.action(None)
        self.assertRaises(PFAUserException, callme)

        try:
            engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action: {error: "This is bad", code: 12}
''')
            engine.action(None)
            raise Exception
        except PFAUserException as err:
            self.assertEqual(err.code, 12)

        def callme2():
            engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: true
  then:
    - {error: "This is bad"}
''')
            engine.action(None)
        self.assertRaises(PFAUserException, callme2)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: "null"
action:
  if: false
  then:
    - {error: "This is bad"}
''')
        self.assertEqual(engine.action(None), None)

        engine, = PFAEngine.fromYaml('''
input: "null"
output: string
action:
  if: false
  then:
    - {error: "This is bad"}
    - [hello]
  else:
    - [there]
''')
        self.assertEqual(engine.action(None), "there")

    def testMinimallyWork(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - input
''')
        self.assertEqual(engine.action("hello"), "hello")

    def testHandleNestedScopes(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - do:
    - input
''')
        self.assertEqual(engine.action("hello"), "hello")

    def testCallFunctions2(self):
        engine, = PFAEngine.fromYaml('''
input: double
output: double
action:
  - {+: [input, input]}
''')
        self.assertEqual(engine.action(2), 4)

    def testIdentifyTypeErrors1(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: double
output: string
action:
  - {+: [input, input]}
'''))

    def testIdentifyTypeErrors2(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: string
action:
  - {+: [input, input]}
'''))

    def testDefineFunctions(self):
        engine, = PFAEngine.fromYaml('''
input: double
output: double
action:
  - {u.plus: [input, input]}
fcns:
  plus:
    params: [{x: double}, {y: double}]
    ret: double
    do:
      - {+: [x, y]}
  minus:
    params: [{x: double}, {y: double}]
    ret: double
    do:
      - {-: [x, y]}
''')
        self.assertEqual(engine.action(2), 4)

    def testMinimallyWorkForEmitTypeEngines(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
method: emit
action:
  - {emit: [input]}
  - input
''')

        out = []
        engine.emit = lambda x: out.append(x)
        engine.action("hello")
        self.assertEqual(out, ["hello"])

    def testEmitInUserFunctions(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
method: emit
action:
  - emit: [input]
  - u.callme: [input]
fcns:
  callme:
    params:
      - x: string
    ret: "null"
    do:
      - emit: [x]
''')

        out = []
        engine.emit = lambda x: out.append(x)
        engine.action("hello")
        self.assertEqual(out, ["hello", "hello"])

    def testMinimallyWorkForFoldTypeEngines(self):
        engine, = PFAEngine.fromYaml('''
input: double
output: double
method: fold
zero: 0
action:
  - {+: [input, tally]}
''')
        
        self.assertEqual(engine.action(5), 5.0)
        self.assertEqual(engine.action(3), 8.0)
        self.assertEqual(engine.action(2), 10.0)
        self.assertEqual(engine.action(20), 30.0)

        engine.tally = 1.0
        self.assertEqual(engine.action(5), 6.0)

    def testRefuseTallyInUserFunctions(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: double
output: double
method: fold
zero: 0
action:
  - u.callme: [input]
fcns:
  callme:
    params:
      - x: double
    ret: double
    do:
      - {+: [x, tally]}
'''))

    def testButPassingTallyInIsFine(self):
        engine, = PFAEngine.fromYaml('''
input: double
output: double
method: fold
zero: 0
action:
  - u.callme: [input, tally]
fcns:
  callme:
    params:
      - x: double
      - t: double
    ret: double
    do:
      - {+: [x, t]}
''')
        
        self.assertEqual(engine.action(5), 5.0)
        self.assertEqual(engine.action(3), 8.0)
        self.assertEqual(engine.action(2), 10.0)
        self.assertEqual(engine.action(20), 30.0)

        engine.tally = 1.0
        self.assertEqual(engine.action(5), 6.0)

    def testExtractDeepWithinAnObject(self):
        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {attr: input, path: [[one], 2, x]}
''')
        self.assertEqual(engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}), 2)

    def testExtractFromAnOnTheFlyGeneratedObject(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: [two]}}
  - attr:
      value: {"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}
      type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
    path: [[one], 2, x]
''')
        self.assertEqual(engine.action(None), 2)
        
    def testNotAcceptBadIndexes(self):
        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {attr: input, path: [[one], 3, x]}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}))

        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [TWO]}}
  - {attr: input, path: [[one], 2, x]}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}))

    def testNotAcceptBadIndexTypes(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {attr: input, path: [[ONE], 2, x]}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {attr: input, path: [[one], x, x]}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {attr: input, path: [[one], 2, 2]}
'''))

    def testChangeADeepObject(self):
        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {let: {something: {attr: input, path: [[one], 2, x], to: 999}}}
  - {attr: something, path: [[one], 2, x]}
''')
        self.assertEqual(engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}), 999)

    def testChangeAnOnTheFlyGeneratedObject(self):
        engine, = PFAEngine.fromYaml('''
input: "null"
output: int
action:
  - {let: {x: [two]}}
  - let:
      something:
        attr:
          value: {"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}
          type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
        path: [[one], 2, x]
        to: 999
  - {attr: something, path: [[one], 2, x]}
''')
        self.assertEqual(engine.action(None), 999)

    def testChangeADeepObjectWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {let: {something: {attr: input, path: [[one], 2, x], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}}}}
  - {attr: something, path: [[one], 2, x]}
''')
        self.assertEqual(engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}), 3)

    def testChangeADeepObjectWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}
output: int
action:
  - {let: {x: [two]}}
  - {let: {something: {attr: input, path: [[one], 2, x], to: {fcnref: u.inc}}}}
  - {attr: something, path: [[one], 2, x]}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action({"one": [{"zero": 0}, {"one": 1}, {"two": 2}]}), 3)

    def testExtractPrivateCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - cell: x
cells:
  x: {type: int, init: 12}
''')
        self.assertEqual(engine.action("whatever"), 12)

    def testExtractPublicCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - cell: x
cells:
  x: {type: int, init: 12, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 12)

    def testExtractDeepPrivateCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
''')
        self.assertEqual(engine.action("two"), 2)

    def testExtractDeepPublicCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}, shared: true}
''')
        self.assertEqual(engine.action("two"), 2)

    def testNotFindNonExistentCells(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - cell: y
cells:
  x: {type: int, init: 12}
'''))

    def testNotAcceptBadIndexes2(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 3, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("two"))

        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("TWO"))

    def testNotAcceptBadIndexTypes2(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[ONE], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], input, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, 2]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
'''))
        
    def testChangePrivateCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: 999}
  - {cell: x}
cells:
  x: {type: int, init: 12}
''')
        self.assertEqual(engine.action("whatever"), 999)

    def testChangePrivateCellsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}}
  - {cell: x}
cells:
  x: {type: int, init: 12}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangePrivateCellsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: {fcnref: u.inc}}
  - {cell: x}
cells:
  x: {type: int, init: 12}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangeDeepPrivateCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: 999}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
''')
        self.assertEqual(engine.action("two"), 999)

    def testChangeDeepPrivateCellsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangeDeepPrivateCellsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: {fcnref: u.inc}}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangePublicCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: 999}
  - {cell: x}
cells:
  x: {type: int, init: 12, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 999)

    def testChangePublicCellsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}}
  - {cell: x}
cells:
  x: {type: int, init: 12, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangePublicCellsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, to: {fcnref: u.inc}}
  - {cell: x}
cells:
  x: {type: int, init: 12, shared: true}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangeDeepPublicCells(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: 999}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}, shared: true}
''')
        self.assertEqual(engine.action("two"), 999)

    def testChangeDeepPublicCellsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}, shared: true}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangeDeepPublicCellsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {cell: x, path: [[one], 2, input], to: {fcnref: u.inc}}
  - {cell: x, path: [[one], 2, input]}
cells:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {one: [{zero: 0}, {one: 1}, {two: 2}]}, shared: true}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("two"), 3)

    def testExtractPrivatePools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}}
''')
        self.assertEqual(engine.action("whatever"), 12)

    def testExtractPublicPools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 12)

    def testExtractDeepPublicPools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
''')
        self.assertEqual(engine.action("two"), 2)

    def testExtractDeepPublicPools2(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}, shared: true}
''')
        self.assertEqual(engine.action("two"), 2)

    def testNotFindNonExistentPools(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: y, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}}
'''))

    def testNotAcceptBadIndexes3(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 3, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("two"))

        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("TWO"))

    def testNotAcceptBadIndexTypes3(self):
        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [ONE], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], input, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
'''))

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, 2]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
'''))

    def testChangePrivatePools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: 999}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}}
''')
        self.assertEqual(engine.action("whatever"), 999)

    def testChangePrivatePoolsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}, init: 0}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangePrivatePoolsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: {fcnref: u.inc}, init: 0}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangeDeepPrivatePools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: 999}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
''')
        self.assertEqual(engine.action("two"), 999)

    def testChangeDeepPrivatePoolsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}, init: 0}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangeDeepPrivatePoolsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: {fcnref: u.inc}, init: 0}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangePublicPools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: 999}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 999)

    def testChangePublicPoolsWithFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}, init: 0}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}, shared: true}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangePublicPoolsWithFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [input], to: {fcnref: u.inc}, init: 0}
  - {pool: x, path: [input]}
pools:
  x: {type: int, init: {whatever: 12}, shared: true}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("whatever"), 13)

    def testChangeDeepPublicPools(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: 999}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}, shared: true}
''')
        self.assertEqual(engine.action("two"), 999)

    def testChangeDeepPublicPoolsFcnDef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}, init: 0}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}, shared: true}
''')
        self.assertEqual(engine.action("two"), 3)

    def testChangeDeepPublicPoolsFcnRef(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
action:
  - {pool: x, path: [[whatever], [one], 2, input], to: {fcnref: u.inc}, init: 0}
  - {pool: x, path: [[whatever], [one], 2, input]}
pools:
  x: {type: {type: record, name: SimpleRecord, fields: [{name: one, type: {type: array, items: {type: map, values: int}}}]}, init: {whatever: {one: [{zero: 0}, {one: 1}, {two: 2}]}}, shared: true}
fcns:
  inc: {params: [{z: int}], ret: int, do: [{+: [z, 1]}]}
''')
        self.assertEqual(engine.action("two"), 3)

    def testHandleNullCases(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - input
cells:
  notempty: {type: "null", init: null}
''')
        engine.action("hey")

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: empty}
  - input
cells:
  notempty: {type: "null", init: null}
'''))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [[set]]}
  - input
cells:
  notempty: {type: {type: map, values: "null"}, init: {this: null, is: null, a: null, set: null}}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [[notinset]]}
  - input
cells:
  notempty: {type: {type: map, values: "null"}, init: {this: null, is: null, a: null, set: null}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [0]}
  - input
cells:
  notempty: {type: {type: array, items: "null"}, init: [null, null, null, null]}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [999]}
  - input
cells:
  notempty: {type: {type: array, items: "null"}, init: [null, null, null, null]}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

    def testHandleNullCases2(self):
        self.assertRaises(PFASyntaxException, lambda: PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: []}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}}
'''))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[shouldfail]]}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], [set]]}
  - input
pools:
  notempty: {type: {type: map, values: "null"}, init: {whatever: {this: null, is: null, a: null, set: null}}}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], [notinset]]}
  - input
pools:
  notempty: {type: {type: map, values: "null"}, init: {whatever: {this: null, is: null, a: null, set: null}}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], 0]}
  - input
pools:
  notempty: {type: {type: array, items: "null"}, init: {whatever: [null, null, null, null]}}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], 999]}
  - input
pools:
  notempty: {type: {type: array, items: "null"}, init: {whatever: [null, null, null, null]}}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

    def testHandleNullCases3(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - input
cells:
  notempty: {type: "null", init: null, shared: true}
''')
        engine.action("hey")

        self.assertRaises(PFASemanticException, lambda: PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: empty}
  - input
cells:
  notempty: {type: "null", init: null, shared: true}
'''))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [[set]]}
  - input
cells:
  notempty: {type: {type: map, values: "null"}, init: {this: null, is: null, a: null, set: null}, shared: true}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [[notinset]]}
  - input
cells:
  notempty: {type: {type: map, values: "null"}, init: {this: null, is: null, a: null, set: null}, shared: true}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [0]}
  - input
cells:
  notempty: {type: {type: array, items: "null"}, init: [null, null, null, null], shared: true}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {cell: notempty}
  - {cell: notempty, path: [999]}
  - input
cells:
  notempty: {type: {type: array, items: "null"}, init: [null, null, null, null], shared: true}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

    def testHandleNullCases4(self):
        self.assertRaises(PFASyntaxException, lambda: PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: []}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}, shared: true}
'''))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}, shared: true}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[shouldfail]]}
  - input
pools:
  notempty: {type: "null", init: {whatever: null}, shared: true}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], [set]]}
  - input
pools:
  notempty: {type: {type: map, values: "null"}, init: {whatever: {this: null, is: null, a: null, set: null}}, shared: true}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], [notinset]]}
  - input
pools:
  notempty: {type: {type: map, values: "null"}, init: {whatever: {this: null, is: null, a: null, set: null}}, shared: true}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], 0]}
  - input
pools:
  notempty: {type: {type: array, items: "null"}, init: {whatever: [null, null, null, null]}, shared: true}
''')
        engine.action("hey")

        engine, = PFAEngine.fromYaml('''
input: string
output: string
action:
  - {pool: notempty, path: [[whatever]]}
  - {pool: notempty, path: [[whatever], 999]}
  - input
pools:
  notempty: {type: {type: array, items: "null"}, init: {whatever: [null, null, null, null]}, shared: true}
''')
        self.assertRaises(PFARuntimeException, lambda: engine.action("hey"))

    def testHandleATypicalUseCase(self):
        engine, = PFAEngine.fromYaml('''
input: string
output: int
method: map
action:
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[y]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[y]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[x]]}
pools:
  tally: {type: int, init: {}}
fcns:
  inc: {params: [{i: int}], ret: int, do: [{+: [i, 1]}]}
''')
        self.assertEqual(engine.action("hey"), 3)

        engine, = PFAEngine.fromYaml('''
input: string
output: int
method: map
action:
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[x]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[y]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[y]], to: {fcnref: u.inc}, init: 0}
  - {pool: tally, path: [[y]]}
pools:
  tally: {type: int, init: {}}
fcns:
  inc: {params: [{i: int}], ret: int, do: [{+: [i, 1]}]}
''')
        self.assertEqual(engine.action("hey"), 2)

    def testStopIntRunningProcess(self):
        def go():
            engine, = PFAEngine.fromYaml('''
input: string
output: "null"
action:
  - for: {x: 0}
    while: {"!=": [x, -5]}
    step: {x: {+: [x, 1]}}
    do: [x]
options:
  timeout: 1000
''')
            engine.action("hey")
        self.assertRaises(PFATimeoutException, go)



        
if __name__ == "__main__":
    unittest.main()

/*
 * Copyright 2015 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.ijs;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;

/** Unit tests for {@link ConvertToTypedInterface}. */
public final class ConvertToTypedInterfaceTest extends CompilerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ConvertToTypedInterface(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testInferAnnotatedTypeFromTypeInference() {
    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");
  }

  public void testExternsDefinitionsRespected() {
    test(externs("/** @type {number} */ var x;"), srcs("x = 7;"), expected(""));
  }

  public void testUnannotatedDeclaration() {
    test("var x;", "/** @const {*} */ var x;");
  }

  public void testSimpleConstJsdocPropagation() {
    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");
    test("/** @const */ var x = true;", "/** @const {boolean} */ var x;");
    test("/** @const */ var x = 'str';", "/** @const {string} */ var x;");
    test("/** @const */ var x = null;", "/** @const {null} */ var x;");
    test("/** @const */ var x = void 0;", "/** @const {void} */ var x;");
    test("/** @const */ var x = /a/;", "/** @const {!RegExp} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");

    test(
        "/** @const */ var x = cond ? true : 5;",
        "/** @const {*} */ var x;",
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  public void testConstKeywordWithAnnotatedType() {
    test("/** @type {number} */ const x = 5;", "/** @const {number} */ var x;");
    test("/** @type {!Foo} */ const f = new Foo;", "/** @const {!Foo} */ var f;");
  }

  public void testConstKeywordJsdocPropagation() {
    test("const x = 5;", "/** @const {number} */ var x;");

    test(
        "const x = 5, y = 'str', z = /abc/;",
        lines(
            "/** @const {number} */ var x;",
            "/** @const {string} */ var y;",
            "/** @const {!RegExp} */ var z;"));

    test(
        "const x = cond ? true : 5;",
        "/** @const {*} */ var x;",
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  public void testSplitMultiDeclarations() {
    test("var /** number */ x = 4, /** string */ y = 'str';",
        "/** @type {number} */ var x; /** @type {string} */ var y;");

    test("var /** number */ x, /** string */ y;",
        "/** @type {number} */ var x; /** @type {string} */ var y;");

    test("let /** number */ x = 4, /** string */ y = 'str';",
    "/** @type {number} */ var x; /** @type {string} */ var y;");

    test("let /** number */ x, /** string */ y;",
        "/** @type {number} */ let x; /** @type {string} */ let y;");
  }

  public void testThisPropertiesInConstructors() {
    test(
        "/** @constructor */ function Foo() { /** @const {number} */ this.x; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");

    test(
        "/** @constructor */ function Foo() { this.x = undefined; }",
        "/** @constructor */ function Foo() {} \n /** @const {*} */ Foo.prototype.x;");

    test(
        "/** @constructor */ function Foo() { /** @type {?number} */ this.x = null; this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @type {?number} */ Foo.prototype.x;");


    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = cond ? true : 5; }",
        "/** @constructor */ function Foo() {}  /** @const {*} */ Foo.prototype.x;",
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  public void testNonThisPropertiesInConstructors() {
    test(
        "/** @constructor */ function Foo() { const obj = {}; obj.name = () => 5; alert(obj); }",
        "/** @constructor */ function Foo() {}");
  }

  public void testThisPropertiesInConstructorsAndPrototype() {
    test(
        lines(
            "/** @constructor */ function Foo() { this.x = null; }",
            "/** @type {?number} */ Foo.prototype.x = 5;"),
        "/** @constructor */ function Foo() {} \n /** @type {?number} */ Foo.prototype.x;");

    test(
        lines(
            "/** @constructor */ function Foo() { this.x = null; }",
            "/** @type {?number} */ Foo.prototype.x;"),
        "/** @constructor */ function Foo() {} \n /** @type {?number} */ Foo.prototype.x;");

     test(
         lines(
             "/** @constructor */ function Foo() { this.x = null; }",
             "Foo.prototype.x = 5;"),
         "/** @constructor */ function Foo() {} \n /** @const {*} */ Foo.prototype.x;");
  }

  public void testConstJsdocPropagationForGlobalNames() {
    test(
        "/** @type {!Array<string>} */ var x = []; /** @const */ var y = x;",
        "/** @type {!Array<string>} */ var x; /** @const */ var y = x;");

    test(
        "/** @type {Object} */ var o = {}; /** @type {number} */ o.p = 5; /** @const */ var y = o;",
        "/** @type {Object} */ var o; /** @type {number} */ o.p; /** @const */ var y = o;");

    testWarning(
        "/** @const */ var x = Foo;", ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testConstJsdocPropagationForConstructorNames() {
    test(
        lines(
            "/** @constructor */",
            "function Foo(/** number */ x) {",
            "  /** @const */ this.x = x;",
            "}"),
        lines(
            "/** @constructor */ function Foo(/** number */ x) {}",
            "/** @const {number} */ Foo.prototype.x;"));

    test(
        lines(
            "/** @constructor @param {!Array<string>} arr */",
            "function Foo(arr) {",
            "  /** @const */ this.arr = arr;",
            "}"),
        lines(
          "/** @constructor @param {!Array<string>} arr */ function Foo(arr) {}",
          "/** @const {!Array<string>} */ Foo.prototype.arr;"));

    test(
        lines(
            "class Foo {",
            "  constructor(/** number */ x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}"),
        lines(
            "class Foo {",
            "  constructor(/** number */ x) {}",
            "}",
            "/** @const {number} */ Foo.prototype.x;"));

    test(
        lines(
            "class Foo {",
            "  /** @param {number} x */",
            "  constructor(x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}"),
        lines(
            "class Foo {",
            "  /** @param {number} x */",
            "  constructor(x) {}",
            "}",
            "/** @const {number} */ Foo.prototype.x;"));
  }

  public void testClassMethodsConflictWithOtherAssignment() {
    test(
        lines(
            "class Foo {",
            "  /** @return {number} */",
            "  method() {}",
            "}",
            "Foo.prototype.method = wrap(Foo.prototype.method);"),
        lines(
            "class Foo {",
            "  /** @return {number} */",
            "  method() {}",
            "}",
            ""));

    test(
        lines(
            "class Foo {",
            "  constructor() {",
            "    this.method = wrap(Foo.prototype.method);",
            "  }",
            "  /** @return {number} */",
            "  method() {}",
            "}",
            ""),
        lines(
            "class Foo {",
            "  constructor() {}",
            "  /** @return {number} */",
            "  method() {}",
            "}",
            ""));

  }

  public void testMultipleSameNamedThisProperties() {
    test(
        lines(
            "class Foo {",
            "  constructor(/** number */ x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}",
            "/** @template T */",
            "class Bar {",
            "  constructor(/** T */ x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}"),
        lines(
            "class Foo {",
            "  constructor(/** number */ x) {}",
            "}",
            "/** @const {number} */ Foo.prototype.x;",
            "/** @template T */",
            "class Bar {",
            "  constructor(/** T */ x) {}",
            "}",
            "/** @const {T} */ Bar.prototype.x;"));

  }

  public void testGoogAddSingletonGetter() {
    testSame("class Foo {}  goog.addSingletonGetter(Foo);");
  }

  public void testLegacyGoogModule() {
    testSame(
        lines(
            "goog.module('a.b.c');",
            "goog.module.declareLegacyNamespace();",
            "",
            "exports = class {};"));
  }

  public void testConstructorAlias1() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @const */ var FooAlias = Foo;"));
  }

  public void testConstructorAlias2() {
    testSame(
        lines(
            "goog.module('a.b.c');",
            "",
            "/** @constructor */",
            "function Foo() {}",
            "/** @const */ var FooAlias = Foo;"));
  }

  public void testConstructorAlias3() {
    testSame(
        lines(
            "class Foo {}",
            "/** @const */ var FooAlias = Foo;"));
  }

  public void testConstructorAlias4() {
    testSame(
        lines(
            "goog.module('a.b.c');",
            "",
            "class Foo {}",
            "/** @constructor */ var FooAlias = Foo;"));
  }

  public void testConstructorAlias5() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
           "/** @constructor */ var FooAlias = Foo;"));
  }

  public void testConstructorAlias6() {
    testSame(
        lines(
            "goog.provide('a.b.c.Foo');",
            "goog.provide('FooAlias');",
            "",
            "/** @constructor */",
            "a.b.c.Foo = function() {};",
            "",
            "/** @const */ var FooAlias = a.b.c.Foo;"));
  }

  public void testConstructorAlias7() {
    testSame(
        lines(
            "class Foo {}",
            "const FooAlias = Foo;"));
  }

  public void testConstructorAlias8() {
    testSame(
        lines(
            "goog.module('a.b.c');",
            "",
            "class Foo {}",
            "const FooAlias = Foo;"));
  }

  public void testRequireAlias1() {
    testSame(
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.require('a.b.c.Foo');",
            "",
            "/** @const */ var FooAlias = a.b.c.Foo;"));
  }

  public void testRequireAlias2() {
    testSame(
        lines(
            "goog.provide('FooAlias');",
            "goog.provide('BarAlias');",
            "",
            "goog.require('a.b.c');",
            "",
            "/** @const */ var FooAlias = a.b.c.Foo;",
            "/** @const */ var BarAlias = a.b.c.Bar;"));
  }

  public void testRequireAlias3() {
    testSame(
        lines(
            "goog.module('FooAlias');",
            "",
            "const Foo = goog.require('a.b.c.Foo');",
            "",
            "exports = Foo;"));
  }

  public void testRequireAlias4() {
    testSame(
        lines(
            "goog.module('FooAlias');",
            "",
            "const {Foo} = goog.require('a.b.c');",
            "",
            "exports = Foo;"));
  }

  public void testConstPropagationPrivateProperties1() {
    test(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @const @private */ this.x = someComplicatedExpression();",
            "}"),
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @const @private {*} */ Foo.prototype.x;"));
  }

  public void testConstPropagationPrivateProperties2() {
    test(
        lines(
            "goog.provide('a.b.c');",
            "",
            "/** @private @const */",
            "a.b.c.helper_ = someComplicatedExpression();"),
        "goog.provide('a.b.c');   /** @private @const {*} */ a.b.c.helper_;");
  }

  public void testOverrideAnnotationCountsAsDeclaration() {
    testSame(
        lines(
            "goog.provide('x.y.z.Bar');",
            "",
            "goog.require('a.b.c.Foo');",
            "",
            "/** @constructor @extends {a.b.c.Foo} */",
            "x.y.z.Bar = function() {};",
            "",
            "/** @override */",
            "x.y.z.Bar.prototype.method = function(a, b, c) {};"));

    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "const {Foo} = goog.require('a.b.c');",
            "",
            "/** @constructor @extends {Foo} */",
            "const Bar = class {",
            "   /** @override */",
            "   method(a, b, c) {}",
            "}",
            "exports.Bar = Bar;"));

  }

  public void testConstJsdocPropagationForNames_optional() {
    test(
        lines(
            "/** @constructor */",
            "function Foo(/** number= */ opt_x) {",
            "  /** @const */ this.x = opt_x;",
            "}"),
        lines(
            "/** @constructor */ function Foo(/** number= */ opt_x) {}",
            "/** @const {number|undefined} */ Foo.prototype.x;"));

  }

  public void testNotConfusedByOutOfOrderDeclarations() {
    test(
        lines(
            "/** @constructor */",
            "function Foo(/** boolean= */ opt_tag) {",
            "  if (opt_tag) {",
            "    Foo.tag = opt_tag;",
            "  }",
            "}",
            "/** @type {boolean} */ Foo.tag = true;",
            ""),
        lines(
            "/** @constructor */",
            "function Foo(/** boolean= */ opt_tag) {}",
            "/** @type {boolean} */ Foo.tag;"));

  }

  public void testConstJsdocPropagationForNames_rest() {
    test(
        lines(
            "/**",
            " * @constructor",
            " * @param {...number} nums",
            " */",
            "function Foo(...nums) {",
            "  /** @const */ this.nums = nums;",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " * @param {...number} nums",
            " */",
            "function Foo(...nums) {}",
            "/** @const {!Array<number>} */ Foo.prototype.nums;"));
  }

  public void testOptionalRestParamFunction() {
    test(
        lines(
            "/**",
            " * @param {?Object} o",
            " * @param {string=} str",
            " * @param {number=} num",
            " * @param {...!Object} rest",
            " */",
            "function foo(o, str = '', num = 5, ...rest) {}"),
        lines(
            "/**",
            " * @param {?Object} o",
            " * @param {string=} str",
            " * @param {number=} num",
            " * @param {...!Object} rest",
            " */",
            "function foo(o, str, num, ...rest) {}"));
  }

  public void testConstJsdocPropagationForNames_defaultValue() {
    test(
        lines(
            "/**",
            " * @constructor",
            " * @param {string=} str",
            " */",
            "function Foo(str = '') {",
            "  /** @const */ this.s = str;",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " * @param {string=} str",
            " */",
            "function Foo(str) {}",
            "/** @const {string} */ Foo.prototype.s;"));

    test(
        lines(
            "class Foo {",
            "  /** @param {string=} str */",
            "  constructor(str = '') {",
            "    /** @const */ this.s = str;",
            "  }",
            "}"),
        lines(
            "class Foo {",
            "  /** @param {string=} str */",
            "  constructor(str) {}",
            "}",
            "/** @const {string} */ Foo.prototype.s;"));

  }

  public void testConstWithDeclaredTypes() {
    test("/** @const @type {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const @return {void} */ var f = compute();", "/** @const @return {void} */ var f;");
    test("/** @const @this {Array} */ var f = compute();", "/** @const @this {Array} x */ var f;");

    test(
        "/** @const @param {number} x */ var f = compute();",
        "/** @const @param {number} x */ var f;");

    test(
        "/** @const @constructor x */ var Foo = createConstructor();",
        "/** @const @constructor x */ var Foo;");
  }

  public void testRemoveUselessStatements() {
    test("34", "");
    test("'str'", "");
    test("({x:4})", "");
    test("debugger;", "");
    test("throw 'error';", "");
    test("label: debugger;", "");
  }

  public void testRemoveUnnecessaryBodies() {
    test("function f(x,y) { /** @type {number} */ z = x + y; return z; }", "function f(x,y) {}");

    test(
        "/** @return {number} */ function f(/** number */ x, /** number */ y) { return x + y; }",
        "/** @return {number} */ function f(/** number */ x, /** number */ y) {}");

    test(
        "class Foo { method(/** string */ s) { return s.split(','); } }",
        "class Foo { method(/** string */ s) {} }");
  }

  public void testRemoveEmptyMembers() {
    test(
        "class Foo { ;; method(/** string */ s) {};; }",
        "class Foo { method(/** string */ s) {} }");
  }

  public void testEs6Modules() {
    testSame("export default class {}");

    testSame("import Foo from 'foo';");

    testSame("export class Foo {}");

    testSame("import {Foo} from 'foo';");

    testSame(
        lines(
            "import {Baz} from 'baz';",
            "",
            "export /** @constructor */ function Foo() {}",
            "/** @type {!Baz} */ Foo.prototype.baz"));

    testSame(
        lines(
            "import {Bar, Baz} from 'a/b/c';",
            "",
            "class Foo extends Bar {",
            "  /** @return {!Baz} */ getBaz() {}",
            "}",
            "",
            "export {Foo};"));

    test(
        lines(
            "export class Foo {",
            "  /** @return {number} */ getTime() { return Date.now(); }",
            "}",
            "export default /** @return {number} */ () => 6",
            "const BLAH = 'foobar';",
            "export {BLAH};"),
        lines(
            "export class Foo {",
            "  /** @return {number} */ getTime() {}",
            "}",
            "export default /** @return {number} */ () => {}",
            "/** @const {string} */ var BLAH;",
            "export {BLAH};"));
  }

  public void testEs6ModulesExportedNameDeclarations() {
    testSame("/** @type {number} */ export let x;");
    testSame("/** @type {number} */ export var x;");

    test("/** @type {number} */ export var x = 5;", "/** @type {number} */ export var x;");
    test("/** @type {number} */ export const X = 5;", "/** @const {number} */ export var X;");
    //TODO(blickly): Ideally, we would leave let declarations alone
    test("/** @type {number} */ export let x = 5;", "/** @type {number} */ export var x;");

    test(
        "/** @type {number} */ export const X = 5, Y = z;",
        "/** @const {number} */ export var X; /** @const {number} */ export var Y;");
  }

  public void testGoogModules() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "/** @constructor */ function Foo() {}",
            "",
            "exports = Foo;"));

    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('a.b.c');",
            "",
            "/** @constructor */ function Foo() {}",
            "/** @type {!Baz} */ Foo.prototype.baz",
            "",
            "exports = Foo;"));

    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "const {Bar, Baz} = goog.require('a.b.c');",
            "",
            "/** @constructor */ function Foo() {}",
            "/** @type {!Baz} */ Foo.prototype.baz",
            "",
            "exports = Foo;"));

    testSame(
        new String[] {
          lines(
              "goog.module('a.b.c');",
              "/** @constructor */ function Foo() {}",
              "Foo.prototype.display = function() {};",
              "exports = Foo;"),
          lines(
              "goog.module('x.y.z');",
              "/** @constructor */ function Foo() {}",
              "Foo.prototype.display = function() {};",
              "exports = Foo;"),
        });

    testSame(
        new String[] {
          lines(
              "/** @constructor */ function Foo() {}",
              "Foo.prototype.display = function() {};"),
          lines(
              "goog.module('x.y.z');",
              "/** @constructor */ function Foo() {}",
              "Foo.prototype.display = function() {};",
              "exports = Foo;"),
        });

    test(
        new String[] {
          lines(
              "goog.module('a.b.c');",
              "/** @constructor */ function Foo() {",
              "  /** @type {number} */ this.x = 5;",
              "}",
              "exports = Foo;"),
          lines(
              "goog.module('x.y.z');",
              "/** @constructor */ function Foo() {",
              "  /** @type {number} */ this.x = 99;",
              "}",
              "exports = Foo;"),
        },
        new String[] {
          lines(
              "goog.module('a.b.c');",
              "/** @constructor */ function Foo() {}",
              "/** @type {number} */ Foo.prototype.x;",
              "exports = Foo;"),
          lines(
              "goog.module('x.y.z');",
              "/** @constructor */ function Foo() {}",
              "/** @type {number} */ Foo.prototype.x;",
              "exports = Foo;"),
        });
  }

  public void testGoogModulesWithTypedefExports() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "/** @typedef {number} */",
            "exports.Foo;"));
  }

  public void testGoogModulesWithUndefinedExports() {
    testWarning(
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "exports = (new Baz).getFoobar();"),
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

   testWarning(
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "exports.foobar = (new Baz).getFoobar();"),
       ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testGoogModulesWithAnnotatedUninferrableExports() {
    test(
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "/** @type {Foobar} */",
            "exports.foobar = (new Baz).getFoobar();"),
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "/** @type {Foobar} */",
            "exports.foobar;"));

    test(
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "/** @type {Foobar} */",
            "exports = (new Baz).getFoobar();"),
        lines(
            "goog.module('x.y.z');",
            "",
            "const Baz = goog.require('x.y.z.Baz');",
            "const Foobar = goog.require('f.b.Foobar');",
            "",
            "/** @type {Foobar} */",
            "exports = /** @const {?} */ (0);"));
  }

  public void testCrossFileModifications() {
    test(
        lines(
            "goog.module('a.b.c');",
            "othermodule.modify.something = othermodule.modify.something + 1;"),
        "goog.module('a.b.c');");

    test(
        lines(
            "goog.module('a.b.c');",
            "class Foo {}",
            "Foo.something = othermodule.modify.something + 1;",
            "exports = Foo;"),
        lines(
            "goog.module('a.b.c');",
            "class Foo {}",
            "/** @const {*} */ Foo.something",
            "exports = Foo;"));

    test(
        lines(
            "goog.provide('a.b.c');",
            "otherfile.modify.something = otherfile.modify.something + 1;"),
        "goog.provide('a.b.c');");

    test(
        lines(
            "goog.provide('a.b.c');",
            "a.b.c.something = otherfile.modify.something + 1;"),
        lines(
            "goog.provide('a.b.c');",
            "/** @const {*} */ a.b.c.something;"));
  }

  public void testRemoveCalls() {
    test("alert('hello'); window.clearTimeout();", "");

    testSame("goog.provide('a.b.c');");

    testSame("goog.provide('a.b.c'); goog.require('x.y.z');");
  }

  public void testEnums() {
    test(
        "/** @const @enum {number} */ var E = { A: 1, B: 2, C: 3};",
        "/** @const @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    test(
        "/** @enum {number} */ var E = { A: foo(), B: bar(), C: baz()};",
        "/** @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    // NOTE: This pattern typechecks when found in externs, but not for code.
    // Since the goal of this pass is intended to be used as externs, this is acceptable.
    test(
        "/** @enum {string} */ var E = { A: 'hello', B: 'world'};",
        "/** @enum {string} */ var E = { A: 0, B: 0};");

    test(
        "/** @enum {Object} */ var E = { A: {b: 'c'}, D: {e: 'f'} };",
        "/** @enum {Object} */ var E = { A: 0, D: 0};");

    test(
        "var x = 7; /** @enum {number} */ var E = { A: x };",
        "/** @const {*} */ var x; /** @enum {number} */ var E = { A: 0 };");
  }

  public void testTryCatch() {
    test(
        "try { /** @type {number} */ var n = foo(); } catch (e) { console.log(e); }",
        "/** @type {number} */ var n;");

    test(
        lines(
            "try {",
            "  /** @type {number} */ var start = Date.now();",
            "  doStuff();",
            "} finally {",
            "  /** @type {number} */ var end = Date.now();",
            "}"),
        "/** @type {number} */ var start; /** @type {number} */ var end;");
  }

  public void testTemplatedClass() {
    test(
        lines(
            "/** @template T */",
            "const Foo = goog.defineClass(null, {",
            "  /** @param {T} x */",
            "  constructor: function(x) { /** @const */ this.x = x;},",
            "});"),
        lines(
            "/** @template T */",
            "const Foo = goog.defineClass(null, {",
            "  /** @param {T} x */",
            "  constructor: function(x) {},",
            "});",
            "/** @const {T} */ Foo.prototype.x;"));

    test(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @param {T} x */",
            "  constructor(x) { /** @const */ this.x = x;}",
            "}"),
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @param {T} x */",
            "  constructor(x) {}",
            "}",
            "/** @const {T} */ Foo.prototype.x;"));
  }

  public void testConstructorBodyWithThisDeclaration() {
    test(
        "/** @constructor */ function Foo() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ function Foo() {} /** @type {number} */ Foo.prototype.num;");

    test(
        "/** @constructor */ function Foo(b) { if (b) { /** @type {number} */ this.num = 5; } }",
        "/** @constructor */ function Foo(b) {} /** @type {number} */ Foo.prototype.num;");

    test(
        "/** @constructor */ let Foo = function() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ let Foo = function() {}; /** @type {number} */ Foo.prototype.num;");

    test(
        "class Foo { constructor() { /** @type {number} */ this.num = 5;} }",
        "class Foo { constructor() {} } /** @type {number} */ Foo.prototype.num;");

    test(
        lines(
            "const Foo = goog.defineClass(null, {",
            "  constructor: function() { /** @type {number} */ this.num = 5;},",
            "});"),
        lines(
            "const Foo = goog.defineClass(null, {",
            "  constructor: function() {},",
            "});",
            "/** @type {number} */ Foo.prototype.num;"));

    test(
        lines(
            "ns.Foo = goog.defineClass(null, {",
            "  constructor: function() { /** @type {number} */ this.num = 5;},",
            "});"),
        lines(
            "ns.Foo = goog.defineClass(null, {",
            "  constructor: function() {},",
            "});",
            "/** @type {number} */ ns.Foo.prototype.num;"));

    test(
        lines(
            "const Foo = goog.defineClass(null, {",
            "  /** @return {number} */",
            "  foo: function() { return 5;},",
            "});"),
        lines(
            "const Foo = goog.defineClass(null, {",
            "  /** @return {number} */",
            "  foo: function() {},",
            "});"));

    test(
        lines(
            "const Foo = goog.defineClass(null, {",
            "  /** @return {number} */",
            "  foo() { return 5;},",
            "});"),
        lines(
            "const Foo = goog.defineClass(null, {",
            "  /** @return {number} */",
            "  foo() {},",
            "});"));
  }

  public void testConstructorBodyWithoutThisDeclaration() {
    test(
        "/** @constructor */ function Foo(o) { o.num = 8; var x = 'str'; }",
        "/** @constructor */ function Foo(o) {}");
  }

  public void testIIFE() {
    test("(function(){ /** @type {number} */ var n = 99; })();", "");
  }

  public void testConstants() {
    test("/** @const {number} */ var x = 5;", "/** @const {number} */ var x;");
  }

  public void testDefines() {
    // NOTE: This is another pattern that is only allowed in externs.
    test("/** @define {number} */ var x = 5;", "/** @define {number} */ var x;");

    test("/** @define {number} */ goog.define('goog.BLAH', 5);",
         "/** @define {number} */ goog.define('goog.BLAH');");
  }

  public void testNestedBlocks() {
    test("{ const x = foobar(); }", "");

    test("{ /** @const */ let x = foobar(); }", "");

    test("{ /** @const */ let x = foobar(); x = foobaz(); }", "");

    testWarning("{ /** @const */ var x = foobar(); }",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testGoogProvidedTopLevelSymbol() {
    testSame("goog.provide('Foo');  /** @constructor */ Foo = function() {};");
  }

  public void testIfs() {
    test(
        "if (true) { var /** number */ x = 5; }",
        "/** @type {number} */ var x;");

    test(
        "if (true) { var /** number */ x = 5; } else { var /** string */ y = 'str'; }",
        "/** @type {number} */ var x; /** @type {string} */ var  y;");

    test(
        "if (true) { if (false) { var /** number */ x = 5; } }",
        "/** @type {number} */ var x;");

    test(
        "if (true) {} else { if (false) {} else { var /** number */ x = 5; } }",
        "/** @type {number} */ var x;");
  }

  public void testLoops() {
    test("while (true) { foo(); break; }", "");

    test("for (var i = 0; i < 10; i++) { var field = 88; }",
        "/** @const {*} */ var i; /** @const {*} */ var field;");

    test("for (var i = 0, arraySize = getSize(); i < arraySize; i++) { foo(arr[i]); }",
        "/** @const {*} */ var i; /** @const {*} */ var arraySize;");

    test(
        "while (i++ < 10) { var /** number */ field = i; }",
        "/** @type {number } */ var field;");

    test(
        "do { var /** number */ field = i; } while (i++ < 10);",
        "/** @type {number } */ var field;");

    test(
        "for (var /** number */ i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {number} */ var i; /** @type {number } */ var field;");

    test(
        "for (i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {number } */ var field;");

    test(
        "for (var i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @const {*} */ var i; /** @type {number } */ var field;");
  }

  public void testSymbols() {
    testSame("const sym = Symbol();");

    testSame("/** @const */ var sym = Symbol();");

    test("const sym = Symbol(computeDescription());", "const sym = Symbol();");

    test(
        "/** @type {symbol} */ var sym = Symbol.for(computeDescription());",
        "/** @type {symbol} */ var sym;");
  }

  public void testNamespaces() {
    testSame("/** @const */ var ns = {}; /** @return {number} */ ns.fun = function(x,y,z) {}");

    testSame("/** @const */ var ns = {}; ns.fun = function(x,y,z) {}");

    test(
        "/** @const */ var ns = ns || {}; ns.fun = function(x,y,z) {}",
        "/** @const */ var ns = {}; ns.fun = function(x,y,z) {}");
  }

  public void testNonemptyNamespaces() {
    testSame("/** @const */ var ns = {fun: function(x,y,z) {}}");

    test(
        "/** @const */ var ns = {/** @type {number} */ n: 5};",
        "/** @const */ var ns = {/** @type {number} */ n: 0};");

    // NOTE: This pattern typechecks when found in externs, but not for code.
    // Since the goal of this pass is intended to be used as externs, this is acceptable.
    test(
        "/** @const */ var ns = {/** @type {string} */ s: 'str'};",
        "/** @const */ var ns = {/** @type {string} */ s: 0};");

    test(
        "/** @const */ var ns = {/** @const */ s: 'blahblahblah'};",
        "/** @const */ var ns = {/** @const {string} */ s: 0};");

    test(
        "/** @const */ var ns = {untyped: foo()};",
        "/** @const */ var ns = {/** @const {*} */ untyped: 0};");

  }

  public void testConstKeywordNamespaces() {
    testSame("const ns = {}; /** @return {number} */ ns.fun = function(x,y,z) {}");
    testSame("const ns = { /** @return {number} */ fun : goog.abstractMethod };");
    testSame("const ns = {fun: function(x,y,z) {}}");
    testSame("const ns = { /** @return {number} */ fun(x,y,z) {}}");
  }

  public void testRemoveIgnoredProperties() {
    test(
        "/** @const */ var ns = {}; /** @return {number} */ ns['fun'] = function(x,y,z) {}",
        "/** @const */ var ns = {};");

    test(
        "/** @constructor */ function Foo() {} Foo.prototype['fun'] = function(x,y,z) {}",
        "/** @constructor */ function Foo() {}");

    test(
        "/** @constructor */ function Foo() {} /** @type {str} */ Foo['prototype'].method;",
        "/** @constructor */ function Foo() {}");
  }

  public void testRemoveRepeatedProperties() {
    test(
        "/** @const */ var ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @type {number} */ ns.x;");

    test(
        "/** @const */ var ns = {}; ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @const {*} */ ns.x;");

    test(
        "const ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "const ns = {}; /** @type {number} */ ns.x;");
  }

  public void testRemoveRepeatedDeclarations() {
    test("/** @type {number} */ var x = 4; var x = 7;", "/** @type {number} */ var x;");

    test("/** @type {number} */ var x = 4; x = 7;", "/** @type {number} */ var x;");

    test("var x = 4; var x = 7;", "/** @const {*} */ var x;");

    test("var x = 4; x = 7;", "/** @const {*} */ var x;");
  }

  public void testArrowFunctions() {
    testSame("/** @return {void} */ const f = () => {}");

    test(
        "/** @return {number} */ const f = () => 5", "/** @return {number} */ const f = () => {}");

    test(
        "/** @return {string} */ const f = () => { return 'str' }",
        "/** @return {string} */ const f = () => {}");
  }

  public void testDontRemoveGoogModuleContents() {
    testWarning(
        "goog.module('x.y.z'); var C = goog.require('a.b.C'); exports = new C;",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testSame("goog.module('x.y.z.Foo'); exports = class {};");

    testSame("goog.module('x.y.z'); exports.Foo = class {};");

    testSame("goog.module('x.y.z.Foo'); class Foo {} exports = Foo;");

    testSame("goog.module('x.y.z'); class Foo {} exports.Foo = Foo;");

    testSame("goog.module('x.y.z'); class Foo {} exports = {Foo};");

    testSame(
        lines(
            "goog.module('x.y.z');",
            "const C = goog.require('a.b.C');",
            "class Foo extends C {}",
            "exports = Foo;"));
  }

  public void testDontPreserveUnknownTypeDeclarations() {
    testSame(
        "goog.forwardDeclare('MyType'); /** @type {MyType} */ var x;");

    test(
        "goog.addDependency('zzz.js', ['MyType'], []); /** @type {MyType} */ var x;",
        "/** @type {MyType} */ var x;");

    // This is OK, because short-import goog.forwardDeclares don't declare a type.
    testSame("goog.module('x.y.z'); var C = goog.forwardDeclare('a.b.C'); /** @type {C} */ var c;");
  }

  public void testAliasOfRequirePreserved() {
    testSame(
        lines(
            "goog.provide('a.b.c');",
            "",
            "goog.require('ns.Foo');",
            "",
            "/** @const */",
            "a.b.c.FooAlias = ns.Foo;"));

    testSame(
        lines(
            "goog.provide('a.b.c');",
            "",
            "goog.require('ns.Foo');",
            "",
            "/** @constructor */",
            "a.b.c.FooAlias = ns.Foo;"));

    testSame(
        lines(
            "goog.module('mymod');",
            "",
            "const {Foo} = goog.require('ns.Foo');",
            "",
            "/** @const */",
            "var FooAlias = Foo;",
            "",
            "/** @param {!FooAlias} f */",
            "exports = function (f) {};"));


    testSame(
        lines(
            "goog.module('mymod');",
            "",
            "var Foo = goog.require('ns.Foo');",
            "",
            "/** @constructor */",
            "var FooAlias = Foo;",
            "",
            "/** @param {!FooAlias} f */",
            "exports = function (f) {};"));
  }

  public void testAliasOfNonRequiredName() {
    testWarning(
        lines(
            "goog.provide('a.b.c');",
            "",
            "/** @const */",
            "a.b.c.FooAlias = ns.Foo;"),
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testWarning(
        lines(
            "goog.provide('a.b.c');",
            "",
            "/** @constructor */",
            "a.b.c.Bar = function() {",
            "  /** @const */",
            "  this.FooAlias = ns.Foo;",
            "};"),
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testWarning(
        lines(
            "goog.module('a.b.c');",
            "",
            "class FooAlias {",
            "  constructor() {",
            "    /** @const */",
            "    this.FooAlias = window.Foo;",
            "  }",
            "};"),
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testDuplicateClassMethods() {
    test(
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.prototype.method = function() {};",
            "",
            "Foo = class {",
            "  method() {}",
            "};"),
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.prototype.method = function() {};",
            ""));
  }

  public void testGoogScopeLeftoversAreRemoved() {
    test(
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "/** @const */ var $jscomp = $jscomp || {};",
            "/** @const */ $jscomp.scope = {};",
            "",
            "$jscomp.scope.strayVariable = function() {};",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"),
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"));

    test(
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "/** @const */ var $jscomp = $jscomp || {};",
            "/** @const */ $jscomp.scope = {};",
            "",
            "/** @constructor */",
            "$jscomp.scope.strayCtor = function() { this.x = 5; };",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"),
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"),
        warning(ConvertToTypedInterface.GOOG_SCOPE_HIDDEN_TYPE));

    test(
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "/** @const */ var $jscomp = $jscomp || {};",
            "/** @const */ $jscomp.scope = {};",
            "",
            "$jscomp.scope.strayClass = class {",
            "  constructor() { this.x = 5 };",
            "  method() {};",
            "};",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"),
        lines(
            "goog.provide('a.b.c.d.e.f.g');",
            "",
            "a.b.c.d.e.f.g.Foo = class {};"),
    warning(ConvertToTypedInterface.GOOG_SCOPE_HIDDEN_TYPE));
  }

  public void testDestructuringDoesntCrash() {
    test(
        lines(
            "goog.module('a.b.c');",
            "",
            "const Enum = goog.require('Enum');",
            "const Foo = goog.require('Foo');",
            "",
            "const {A, B} = Enum;",
            "",
            "/** @type {Foo} */",
            "exports.foo = use(A, B);",
            ""),
        lines(
            "goog.module('a.b.c');",
            "",
            "const Enum = goog.require('Enum');",
            "const Foo = goog.require('Foo');",
            "",
            "/** @type {Foo} */",
            "exports.foo;",
            ""));
  }

  public void testSameNamedStaticAndNonstaticMethodsDontCrash() {
    testSame(
        lines(
            "const Foo = class {",
            "  static method() {}",
            "  method() {}",
            "}",
            ""));
  }

  public void testRedeclarationOfClassMethodDoesntCrash() {
    test(
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @private */",
            "    this.handleEvent_ = this.handleEvent_.bind(this);",
            "  }",
            "  /** @private @param {Event} e */",
            "  handleEvent_(e) {}",
            "}",
            ""),
        lines(
            "class Foo {",
            "  constructor() {}",
            "  /** @private @param {Event} e */",
            "  handleEvent_(e) {}",
            "}",
            ""));

    test(
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @param {Event} e */",
            "    this.handleEvent_ = function (e) {};",
            "  }",
            "  handleEvent_(e) {}",
            "}",
            ""),
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @param {Event} e */",
            "    this.handleEvent_ = function (e) {};",
            "  }",
            "}",
            ""));
  }

  public void testGoogGlobalTyped() {
    testSame("/** @const */ var goog = {}; /** @const */ goog.global = this;");
  }


  public void testAnonymousClassDoesntCrash() {
    test(
        "let Foo = fooFactory(class { constructor() {} });",
        "/** @const {*} */ var Foo;");

    test(
        lines(
            "let Foo = fooFactory(class {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.n = 5;",
            "  }",
            "});",
            ""),
        "/** @const {*} */ var Foo;");

    test(
        lines(
            "/** @type {function(new:Int)} */",
            "let Foo = fooFactory(class {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.n = 5;",
            "  }",
            "});",
            ""),
        "/** @type {function(new:Int)} */ var Foo;");
  }

  public void testComputedPropertyInferenceDoesntCrash() {
    test(
        "const SomeMap = { [foo()]: 5 };",
        "const SomeMap = {};");

    test(
        "const SomeBagOfMethods = { /** @return {number} */ method() { return 5; } };",
        "const SomeBagOfMethods = { /** @return {number} */ method() {} };");

    test(
        "const SomeBagOfMethods = { /** @return {number} */ get x() { return 5; } };",
        "const SomeBagOfMethods = { /** @return {number} */ get x() {} };");

    test(
        "const RandomStuff = { [foo()]: 4, method() {}, 9.4: 'bar', set y(x) {} };",
        "const RandomStuff = { method() {}, /** @const @type {*} */ '9.4': 0, set y(x) {} };");
  }


  public void testDescAnnotationCountsAsTyped() {
    test(
        lines(
            "goog.module('a.b.c');",
            "",
            "/** @desc Some description */",
            "exports.MSG_DESCRIPTION = goog.getMsg('Text');",
            ""),
        lines(
            "goog.module('a.b.c');",
            "",
            "/** @const {string} @desc Some description */",
            "exports.MSG_DESCRIPTION;",
            ""));
  }
}

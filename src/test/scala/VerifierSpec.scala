/*
 * UCLID5 Verification and Synthesis Engine
 *
 * Copyright (c) 2017. The Regents of the University of California (Regents).
 * All Rights Reserved.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for educational, research, and not-for-profit purposes,
 * without fee and without a signed licensing agreement, is hereby granted,
 * provided that the above copyright notice, this paragraph and the following two
 * paragraphs appear in all copies, modifications, and distributions.
 *
 * Contact The Office of Technology Licensing, UC Berkeley, 2150 Shattuck Avenue,
 * Suite 510, Berkeley, CA 94720-1620, (510) 643-7201, otl@berkeley.edu,
 * http://ipira.berkeley.edu/industry-info for commercial licensing opportunities.
 *
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF
 * THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF REGENTS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS
 * PROVIDED "AS IS". REGENTS HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT,
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Pramod Subramanyan
 *
 * UCLID verification engine tests.
 *
 */

package uclid
package test

import org.scalatest.FlatSpec
import uclid.{lang => l}

class VerifierSpec extends FlatSpec {
  def nTestsFail(filename: String, nFail : Int) {
    val modules = UclidMain.compile(List(filename), lang.Identifier("main"))
    val mainModule = UclidMain.instantiate(modules, l.Identifier("main"))
    assert (mainModule.isDefined)
    val results = UclidMain.execute(mainModule.get)
    assert (results.count((e) => e.result.isFalse) == nFail)
    assert (results.count((e) => e.result.isUndefined) == 0);
  }

  "test/test-array-0.ucl" should "verify successfully." in {
    nTestsFail("./test/test-array-0.ucl", 0)
  }
  "test/test-bv-assign.ucl" should "verify successfully." in {
    nTestsFail("./test/test-bv-assign.ucl", 0)
  }
  "test/test-bv-fib.ucl" should "verify successfully all but one assertion." in {
    nTestsFail("./test/test-bv-fib.ucl", 1)
  }
  "test/test-case-mc91.ucl" should "verify successfully." in {
    nTestsFail("./test/test-case-mc91.ucl", 0)
  }
  "test/test-forloop.ucl" should "verify successfully." in {
    nTestsFail("./test/test-forloop.ucl", 0)
  }
  "test/test-forloop-0.ucl" should "verify successfully." in {
    nTestsFail("./test/test-forloop-0.ucl", 0)
  }
  "test/test-forloop-1.ucl" should "verify successfully." in {
    nTestsFail("./test/test-forloop-1.ucl", 0)
  }
  "test/test-inliner.ucl" should "verify successfully." in {
    nTestsFail("./test/test-inliner.ucl", 0)
  }
  "test/test-inliner-1.ucl" should "verify successfully." in {
    nTestsFail("./test/test-inliner-1.ucl", 0)
  }
  "test/test-int-fib.ucl" should "verify successfully all but one assertion." in {
    nTestsFail("./test/test-int-fib.ucl", 1)
  }
  "test/test-mc91.ucl" should "verify successfully." in {
    nTestsFail("./test/test-mc91.ucl", 0)
  }
  "test/test-record-1.ucl" should "verify successfully." in {
    nTestsFail("./test/test-record-1.ucl", 0)
  }
  "test/test-tuple-record-1.ucl" should "verify successfully." in {
    nTestsFail("./test/test-tuple-record-1.ucl", 0)
  }
  "test/test-types-0.ucl" should "verify successfully." in {
    nTestsFail("./test/test-types-0.ucl", 0)
  }
  "test/test-functions-1.ucl" should "verify successfully." in {
    nTestsFail("./test/test-functions-1.ucl", 0)
  }
  "test/test-array-1.ucl" should "verify four assertions and fail to verify two assertions." in {
    nTestsFail("./test/test-array-1.ucl", 2)
  }
  "test/test-enum-1.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-enum-1.ucl", 0)
  }
  "test/test-forall-0.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-forall-0.ucl", 0)
  }
  "test/test-exists-0.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-exists-0.ucl", 0)
  }
  "test/test-type2.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-type2.ucl", 0)
  }
  "test/test-modules.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-modules.ucl", 0)
  }
  "test/test-modules-1.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-modules-1.ucl", 0)
  }
  "test/test-type-import.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-type-import.ucl", 0)
  }
  "test/test-const-import.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-const-import.ucl", 0)
  }
  "test/test-if-star.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-if-star.ucl", 0)
  }
  "test/test-procedure-postcondition.ucl" should "verify all but one assertion." in {
    nTestsFail("./test/test-procedure-postcondition.ucl", 1)
  }
  "test/test-mem-inout.ucl" should "verify all assertions." in {
    nTestsFail("./test/test-meminout.ucl", 0)
  }
}
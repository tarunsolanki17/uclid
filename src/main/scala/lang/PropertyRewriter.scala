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
 * Compute the types of each module that is referenced in an instance declaration.
 *
 */
package uclid
package lang


class LTLOperatorArgumentCheckerPass extends ReadOnlyPass[Set[ModuleError]] {
  type T = Set[ModuleError]
  lazy val manager : PassManager = analysis.manager
  lazy val exprTypeChecker = manager.pass("ExpressionTypeChecker").asInstanceOf[ExpressionTypeChecker].pass
  def checkBooleans(operands: List[Expr], context : Scope, in : T) : T = {
    var ret = in
    for (op <- operands) {
      var oType = exprTypeChecker.typeOf(op, context)
      if (!oType.isBool) {
        ret = ret + ModuleError("LTL operator expected argument of type boolean but received argument of type %s.".format(oType.toString), op.position)
      }
    }
    ret
  }
  override def applyOnFuncApp(d : TraversalDirection.T, fapp : FuncApplication, in : T, context : Scope) : T = {
    if (d == TraversalDirection.Up || !context.inLTLSpec) {
      in
    } else {
      var ret = in
      fapp.e match {
        case Identifier(name) =>
          name match {
            case "G" =>
              var numOps = fapp.args.length
              if (numOps != 1) {
                ret = ret + ModuleError("globally operator expected 1 argument but received %s".format(numOps), fapp.position)
              }
            case "X" =>
              var numOps = fapp.args.length
              if (numOps != 1) {
                ret = ret + ModuleError("next operator expected 1 argument but received %s".format(numOps), fapp.position)
              }
            case "U" =>
              var numOps = fapp.args.length
              if (numOps != 2) {
                ret = ret + ModuleError("until operator expected 2 argument but received %s".format(numOps), fapp.position)
              }
            case "F" =>
              var numOps = fapp.args.length
              if (numOps != 1) {
                ret = ret + ModuleError("finally operator expected 1 argument but received %s".format(numOps), fapp.position)
              }
            case _ => in
          }
          checkBooleans(fapp.args, context, ret)
        case _ =>
          in
      }
    }
  }
}

class LTLOperatorArgumentChecker extends ASTAnalyzer(
  "LTLOperatorArgumentChecker", new LTLOperatorArgumentCheckerPass())
{
  override def visit(module : Module, context : Scope) : Option[Module] = {
    val out = visitModule(module, Set.empty[ModuleError], context)
    if (out.nonEmpty) {
      val errors = out.map((me) => (me.msg, me.position)).toList
      throw new Utils.ParserErrorList(errors)
    }
    Some(module)
  }
}

class LTLOperatorRewriterPass extends RewritePass {
  override def rewriteFuncApp(fapp: FuncApplication, context: Scope): Option[Expr] = {
    if (context.inLTLSpec) {
      fapp.e match {
        case Identifier(name : String) => name match {
          case "G" =>
            Some(OperatorApplication(new GloballyTemporalOp, fapp.args))
          case "X" =>
            Some(OperatorApplication(new NextTemporalOp, fapp.args))
          case "U" =>
            Some(OperatorApplication(new UntilTemporalOp, fapp.args))
          case "F" =>
            Some(OperatorApplication(new FinallyTemporalOp, fapp.args))
          case "R" =>
            Some(OperatorApplication(new ReleaseTemporalOp, fapp.args))
          case _ =>
            Some(fapp)
        }
        case _ => Some(fapp) 
      }
    } else {
      Some(fapp)
    }
  }
}

class LTLOperatorRewriter extends ASTRewriter("LTLOperatorRewriter", new LTLOperatorRewriterPass()) {
}


class LTLNegatedNormalFormRewriterPass extends RewritePass {
}

class LTLNegatedNormalFormRewriter extends ASTRewriter(
  "LTLNegatedNormalFormRewriter", new LTLNegatedNormalFormRewriterPass())


class LTLPropertyRewriterPass extends RewritePass {
  var conjCounter = 0
  var circuits : List[(Identifier, Expr)] = List[(Identifier, Expr)]()
  var specMap : Map[SpecDecl, (List[(Identifier, Expr)], Identifier, Identifier)] = Map[SpecDecl, (List[(Identifier, Expr)], Identifier, Identifier)]()

  lazy val manager : PassManager = analysis.manager
  lazy val exprTypeChecker = manager.pass("ExpressionTypeChecker").asInstanceOf[ExpressionTypeChecker].pass

  def negate(expr : Expr) : Expr = OperatorApplication(NegationOp(), List(expr))
  def convertToNNF(expr : Expr) : Expr = {
    def recurse(e :  Expr) = convertToNNF(e)
    expr match {
      case id : Identifier => id
      case eId : ExternalIdentifier => eId
      case lit : Literal => lit
      case tup : Tuple => Tuple(tup.values.map(recurse(_)))
      case opapp : OperatorApplication =>
        val op = opapp.op
        val args = opapp.operands
        lazy val opappP = OperatorApplication(op, args.map(recurse(_)))
        opapp.op match {
          case NegationOp() =>
            Utils.assert(args.size == 1, "Negation operation must have only one operand.")
            val operand = args(0)
            operand match {
              case OperatorApplication(op, operands) =>
                lazy val negOps = operands.map(op => recurse(negate(op)))
                op match {
                  case GloballyTemporalOp() =>
                    OperatorApplication(FinallyTemporalOp(), negOps)
                  case NextTemporalOp() =>
                    OperatorApplication(NextTemporalOp(), negOps)
                  case UntilTemporalOp() =>
                    OperatorApplication(ReleaseTemporalOp(), negOps)
                  case FinallyTemporalOp() =>
                    OperatorApplication(GloballyTemporalOp(), negOps)
                  case ReleaseTemporalOp() =>
                    OperatorApplication(UntilTemporalOp(), negOps)
                  case ConjunctionOp() =>
                    OperatorApplication(DisjunctionOp(), negOps)
                  case DisjunctionOp() =>
                    OperatorApplication(ConjunctionOp(), negOps)
                  case IffOp() =>
                    OperatorApplication(InequalityOp(), operands)
                  case ImplicationOp() =>
                    OperatorApplication(ConjunctionOp(), List(operands(0), negOps(1)))
                  case NegationOp() =>
                    args(0)
                  case EqualityOp() =>
                    OperatorApplication(InequalityOp(), args)
                  case InequalityOp() =>
                    OperatorApplication(EqualityOp(), args)
                  case _ =>
                    opappP
                }
              case _ =>
                opappP
            }
          case _ =>
            opappP
        }
      case arrSel : ArraySelectOperation =>
        ArraySelectOperation(recurse(arrSel.e), arrSel.index.map(recurse(_)))
      case arrUpd : ArrayStoreOperation =>
        ArrayStoreOperation(recurse(arrUpd.e), arrUpd.index.map(recurse(_)), recurse(arrUpd.value))
      case funcApp : FuncApplication =>
        FuncApplication(recurse(funcApp.e), funcApp.args.map(recurse(_)))
      case ite : ITE =>
        ITE(recurse(ite.e), recurse(ite.t), recurse(ite.f))
      case lambda : Lambda =>
        Lambda(lambda.ids, recurse(lambda.e))
    }
  }

  def replace(expr: Expr, spec: SpecDecl, context: Scope) : Expr = {
    if (exprTypeChecker.typeOf(expr, context).isBool) {
      expr match {
        case OperatorApplication(op, operands) =>
          for (opr <- operands) {
            replace(opr, spec, context)
          }
          val ret = Identifier("z" concat conjCounter.toString)
          conjCounter += 1
          circuits = (ret, expr) :: circuits
          ret
        case _ =>
          expr
      }
    } else {
      expr
    }
  }

  def createTseitinExpr(specName : Identifier, expr : Expr, nameProvider : ContextualNameProvider) : 
    (Identifier, List[(Identifier, Expr)], List[(Identifier, Expr)], List[Identifier], List[Identifier], List[Identifier]) = {
    val isExprTemporal = expr match {
      case tExpr : PossiblyTemporalExpr => tExpr.isTemporal
      case ntExpr : Expr => false
    }
    if (!isExprTemporal) {
      val newVar = nameProvider(specName, "z")
      val newImpl = (newVar, expr)
      // FIXME: Revisit this for expressions that don't involve any temporal operators.
      (newVar, List(newImpl), List.empty, List.empty, List.empty, List.empty)
    } else {
      // Recurse on operator applications and create "Tseitin" variables for the inner AST nodes.
      def createTseitinExprOpapp(opapp : OperatorApplication) : (Identifier, List[(Identifier, Expr)], List[(Identifier, Expr)], List[Identifier], List[Identifier], List[Identifier]) = {
        val argResults = opapp.operands.map(arg => createTseitinExpr(specName, arg, nameProvider))
        val args = argResults.map(_._1)
        val argImpls = argResults.flatMap(a => a._2)
        val argNexts = argResults.flatMap(a => a._3)
        val argFaileds = argResults.flatMap(a => a._4)
        val argAccepts = argResults.flatMap(a => a._5)
        val argPendings = argResults.flatMap(a => a._6)
        
        val z = nameProvider(specName, "z")
        val innerExpr = OperatorApplication(opapp.op, args)
        val zImpl = (z, innerExpr)
        if (opapp.op.isInstanceOf[TemporalOperator]) {
          val tOp = opapp.op.asInstanceOf[TemporalOperator]
          def Y(x : Expr) = OperatorApplication(HistoryOperator(), List(x, IntLit(1)))
          def not(x : Expr) = OperatorApplication(NegationOp(), List(x))
          def and(x : Expr, y : Expr) = OperatorApplication(ConjunctionOp(), List(x, y))
          def or(x : Expr, y : Expr) = OperatorApplication(DisjunctionOp(), List(x, y))
          
          tOp match {
            case NextTemporalOp() =>
              // pending = z
              val pendingVar = nameProvider(specName, "pending")
              val pendingNext = (pendingVar, z)
              // failed = Ypending /\ !args(0)
              val failedVar = nameProvider(specName, "failed")
              val failedNext = (failedVar, and(Y(pendingVar), not(args(0))))
              (z, zImpl :: argImpls, pendingNext :: failedNext :: argNexts, failedVar :: argFaileds, argAccepts, pendingVar :: argPendings)
            case GloballyTemporalOp() =>
              // pending = Ypending) \/ z
              val pendingVar = nameProvider(specName, "pending")
              val pendingNext = (pendingVar, or(Y(pendingVar), z))
              // failed = pending /\ !args(0)
              val failedVar = nameProvider(specName, "failed")
              val failedNext = (failedVar, and(pendingVar, not(args(0))))
              (z, zImpl :: argImpls, pendingNext :: failedNext :: argNexts, failedVar :: argFaileds, argAccepts, pendingVar :: argPendings)
            case FinallyTemporalOp() =>
              // pending = (z \/ Ypending) /\ ~args(0)
              val pendingVar = nameProvider(specName, "pending")
              val pendingExpr = and(or(z, Y(pendingVar)), not(args(0)))
              val pendingNext = (pendingVar, pendingExpr)
              // accept = ~pending
              val acceptVar = nameProvider(specName, "accept")
              val acceptNext = (acceptVar, not(pendingVar))
              (z, zImpl :: argImpls, pendingNext :: acceptNext :: argNexts, argFaileds, acceptVar :: argAccepts, pendingVar :: argPendings)
            case _ =>
              // FIXME
              throw new Utils.UnimplementedException("Need more cases here.")
          }
        } else {
          (z, zImpl :: argImpls, argNexts, argFaileds, argAccepts, argPendings)
        }
      }
      // Recurse on a temporal operator.
      val tExpr = expr.asInstanceOf[PossiblyTemporalExpr]
      tExpr match {
        case opapp : OperatorApplication =>
          val op = opapp.op
          op match {
            case op : BooleanOperator =>
              Utils.assert(!op.isQuantified, "Temporal expression within quantifier: " + expr.toString)
              createTseitinExprOpapp(opapp)
            case cOp : ComparisonOperator =>
              createTseitinExprOpapp(opapp)
            case tOp : TemporalOperator =>
              createTseitinExprOpapp(opapp)
            case _ =>
              throw new Utils.AssertionError("Invalid temporal expression: " + expr.toString)
          }
      }
    }
  }

  override def rewriteModule(module: Module, ctx: Scope): Option[Module] = {
    val moduleSpecs = module.decls.collect{ case spec : SpecDecl => spec }
    val ltlSpecs = moduleSpecs.filter(s => s.params.exists(d => d == LTLExprDecorator))
    if (ltlSpecs.size == 0) {
      Some(module)
    } else {
      val otherSpecs = moduleSpecs.filter(s => !s.params.exists(d => d == LTLExprDecorator))
      Some(rewriteSpecs(module, ctx, ltlSpecs, otherSpecs)) 
    }
  }

  def rewriteSpecs(module : Module, ctx : Scope, ltlSpecs : List[SpecDecl], otherSpecs : List[SpecDecl]) : Module = {
    val nameProvider = new ContextualNameProvider(ctx, "ltl")
    val isInit = nameProvider(module.id, "is_init")
    val rewrites = ltlSpecs.map(s => (createTseitinExpr(s.id, convertToNNF(s.expr), nameProvider)))
    val specNames = ltlSpecs.map(s => s.id)
    val monitorVars = rewrites.map(r => (r._4, r._5, r._6))

    def orExpr(a : Expr, b : Expr) : Expr = OperatorApplication(DisjunctionOp(), List(a, b))
    def andExpr(a : Expr, b : Expr) : Expr = OperatorApplication(ConjunctionOp(), List(a, b))
    def notExpr(a : Expr) : Expr = OperatorApplication(NegationOp(), List(a))
    // create the hasFailed and pending variables.
    val monitorExprs = (specNames zip monitorVars).map { 
      p => {
        val failedVars = p._2._1
        val hasFailedVar = nameProvider(p._1, "has_failed")
        val hasFailedExpr : Expr = failedVars.foldLeft(hasFailedVar.asInstanceOf[Expr])((acc, f) => orExpr(acc, f))

        val pendingVars = p._2._3
        val pendingVar = nameProvider(p._1, "PENDING")
        val pendingExpr : Expr = pendingVars.foldLeft(BoolLit(false).asInstanceOf[Expr])((acc, f) => orExpr(acc, f))

        val acceptVars = p._2._2
        val safetyExpr = orExpr(pendingVar, hasFailedVar)
        
        (p._1, (hasFailedVar, hasFailedExpr), (pendingVar, pendingExpr), acceptVars, safetyExpr)
        
      }
    }
    val newInputDecls = InputVarsDecl(rewrites.flatMap(r => r._2).map(_._1), BoolType())
    val monitorVarsInt = rewrites.flatMap(r => r._3).map(_._1)
    val monitorVarsExt = isInit :: monitorExprs.map(_._2._1) ++ monitorExprs.map(_._3._1)
    val varsToInit : List[Identifier] = monitorVarsExt ++ monitorVarsInt
    val newVarDecls = StateVarsDecl(varsToInit, BoolType())
    val newInits = List(AssignStmt(varsToInit.map(LhsId(_)), BoolLit(true) :: List.fill(varsToInit.size - 1)(BoolLit(false))))

    def implExpr(a : Expr, b : Expr) : Expr = OperatorApplication(ImplicationOp(), List(a, b))
    def eqExpr(a : Expr, b : Expr) : Expr = OperatorApplication(EqualityOp(), List(a, b))

    val rootVars = rewrites.map(_._1)
    val rootAssumes = rootVars.map(r => AssumeStmt(eqExpr(r, isInit), None))
    val rewriteImpls = rewrites.flatMap(r => r._2)
    val implicationAssumes = rewriteImpls.map(r => AssumeStmt(implExpr(r._1, r._2), None))

    val assignmentPairs = rewrites.flatMap(r => r._3)
    val newAssigns = assignmentPairs.map(p => AssignStmt(List(LhsId(p._1)), List(p._2)))
    val newHFAssigns = monitorExprs.map(p => AssignStmt(List(LhsId(p._2._1)), List(p._2._2)))
    val newPendingAssigns = monitorExprs.map(p => AssignStmt(List(LhsId(p._3._1)), List(p._3._2)))
    val newNexts = newAssigns ++ newHFAssigns ++ newPendingAssigns

    val otherDecls = module.decls.filter(p => !p.isInstanceOf[SpecDecl] && !p.isInstanceOf[InitDecl] && !p.isInstanceOf[NextDecl]) ++ otherSpecs
    val newInitDecl = InitDecl(module.init.get.body ++ newInits)
    val newNextDecl = NextDecl(module.next.get.body ++ newNexts)
    val newSafetyProperties = monitorExprs.map(p => SpecDecl(p._1, p._5, List(LTLSafetyFragmentDecorator)))
    val moduleDecls = otherDecls ++ List(newInputDecls, newVarDecls, newInitDecl, newNextDecl) ++ newSafetyProperties

    Module(module.id, moduleDecls, module.cmds)
  }
}

class LTLPropertyRewriter extends ASTRewriter(
    "LTLPropertyRewriter", new LTLPropertyRewriterPass())
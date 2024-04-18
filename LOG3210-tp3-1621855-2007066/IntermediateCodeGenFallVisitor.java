package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;


/**
 * Ce visiteur explore l'AST et génère du code intermédiaire.
 *
 * @author Félix Brunet
 * @author Doriane Olewicki
 * @author Quentin Guidée
 * @author Raphaël Tremblay
 * @version 2024.02.26
 */
public class IntermediateCodeGenFallVisitor implements ParserVisitor {
    public static final String FALL = "fall";

    private final PrintWriter m_writer;

    public HashMap<String, VarType> SymbolTable = new HashMap<>();
    public HashMap<String, Integer> EnumValueTable = new HashMap<>();

    private int id = 0;
    private int label = 0;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    private String newID() {
        return "_t" + id++;
    }

    private String newLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }


    @Override
    public Object visit(ASTProgram node, Object data) {
        String programStartLabel = newLabel();
        node.childrenAccept(this, programStartLabel);
        m_writer.println(programStartLabel);
        return null;
    }


    @Override
    public Object visit(ASTDeclaration node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        IntermediateCodeGenFallVisitor.VarType varType;

        if (node.getValue() == null) {
            varName = ((ASTIdentifier) node.jjtGetChild(1)).getValue();
            varType = IntermediateCodeGenFallVisitor.VarType.EnumVar;
        } else
            varType = node.getValue().equals("num") ? IntermediateCodeGenFallVisitor.VarType.Number : IntermediateCodeGenFallVisitor.VarType.Bool;

        SymbolTable.put(varName, varType);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        int childCount = node.jjtGetNumChildren();
        if (childCount == 0) {
            return null;
        }
        if (childCount == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        for (int i = 0; i < childCount; i++) {
            if (i < childCount - 1) {
                String childLabel = newLabel();
                node.jjtGetChild(i).jjtAccept(this, childLabel);
                m_writer.println(childLabel);
            } else {
                node.jjtGetChild(i).jjtAccept(this, data);
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();
        for (int i = 1; i < childCount; i++) {
            String enumConstantName = ((ASTIdentifier) node.jjtGetChild(i)).getValue();
            SymbolTable.put(enumConstantName, IntermediateCodeGenFallVisitor.VarType.EnumType);
            EnumValueTable.put(enumConstantName, i - 1);
        }
        node.childrenAccept(this, data);

        return null;
    }


    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();
        String switchFollowThroughLabel = (String) data;
        String switchVariable = (String) node.jjtGetChild(0).jjtAccept(this, data);

        Vector<String> caseLabels = new Vector<>();
        caseLabels.add(switchFollowThroughLabel);

        for (int i = 1; i < childCount - 1; i++) {
            String caseValue = (String) node.jjtGetChild(i).jjtGetChild(0).jjtAccept(this, caseLabels);
            caseLabels.add(newLabel());
            m_writer.println("if " + switchVariable + " == " + EnumValueTable.get(caseValue) + " goto " + caseLabels.get(caseLabels.size() - 1));
            caseLabels.add(newLabel());
            m_writer.println("goto " + caseLabels.get(caseLabels.size() - 1));
            for (int j = 0; j < caseLabels.size() - 1; j++) {
                m_writer.println(caseLabels.remove(caseLabels.size() - 2));
            }
            node.jjtGetChild(i).jjtAccept(this, caseLabels);
        }
        String caseValue = (String) node.jjtGetChild(childCount - 1).jjtGetChild(0).jjtAccept(this, caseLabels);
        caseLabels.add(newLabel());
        m_writer.println("if " + switchVariable + " == " + EnumValueTable.get(caseValue) + " goto " + caseLabels.get(caseLabels.size() - 1));
        m_writer.println("goto " + caseLabels.get(0));
        int var = caseLabels.size();
        for (int j = 0; j < var - 1; j++) {
            m_writer.println(caseLabels.remove(caseLabels.size() - 1));
        }
        node.jjtGetChild(childCount - 1).jjtAccept(this, caseLabels);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();
        if (childCount == 0) {
            return null;
        }
        for (int i = 1; i < childCount; i++) {
            node.jjtGetChild(i).jjtAccept(this, ((Vector<String>) data).firstElement());
        }
        String lastChildClass = node.jjtGetChild(childCount - 1).getClass().toString();
        boolean isLastChildNotBreak = !lastChildClass.equals("class analyzer.ast.ASTBreakStmt");
        if (isLastChildNotBreak && ((Vector<String>) data).size() > 1) {
            String newLabel = newLabel();
            ((Vector<String>) data).add(newLabel);
            m_writer.println("goto " + newLabel);
        }
        if (((Vector<String>) data).size() > 1) {
            m_writer.println(((Vector<String>) data).remove(1));
        }
        return null;
    }


    @Override
    public Object visit(ASTBreakStmt node, Object data) {
        node.childrenAccept(this, data);
        m_writer.println("goto " + data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();

        switch (childCount) {
            case 1:
                return node.jjtGetChild(0).jjtAccept(this, data);
            case 2:
                node.jjtGetChild(0).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(FALL, (String) data));
                node.jjtGetChild(1).jjtAccept(this, data);
                break;
            case 3:
                String falseBranchLabel = newLabel();
                IntermediateCodeGenFallVisitor.BoolLabel conditionalLabel = new IntermediateCodeGenFallVisitor.BoolLabel(FALL, falseBranchLabel);
                node.jjtGetChild(0).jjtAccept(this, conditionalLabel);
                node.jjtGetChild(1).jjtAccept(this, data);
                m_writer.println("goto " + data);
                m_writer.println(falseBranchLabel);
                node.jjtGetChild(2).jjtAccept(this, data);
                break;
        }

        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String loopStartLabel = newLabel();
        m_writer.println(loopStartLabel);
        node.jjtGetChild(0).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(FALL, (String) data));
        node.jjtGetChild(1).jjtAccept(this, loopStartLabel);
        m_writer.println("goto " + loopStartLabel);

        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String loopStartLabel = newLabel();
        String incrementLabel = newLabel();
        String conditionLabel = newLabel();
        node.jjtGetChild(0).jjtAccept(this, loopStartLabel);
        m_writer.println(loopStartLabel + "TOP");
        node.jjtGetChild(1).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(conditionLabel, (String) data));
        m_writer.println(conditionLabel + "MID");
        node.jjtGetChild(3).jjtAccept(this, incrementLabel);
        m_writer.println(incrementLabel + "MID 2");
        node.jjtGetChild(2).jjtAccept(this, loopStartLabel);
        m_writer.println("goto " + loopStartLabel);

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String variableName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (SymbolTable.get(variableName) == IntermediateCodeGenFallVisitor.VarType.Number) {
            String expressionResult = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(variableName + " = " + expressionResult);
        } else if (SymbolTable.get(variableName) == IntermediateCodeGenFallVisitor.VarType.EnumVar) {
            String enumValue = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(variableName + " = " + EnumValueTable.get(enumValue));
        } else {
            String falseLabel = newLabel();
            IntermediateCodeGenFallVisitor.BoolLabel booleanLabel = new IntermediateCodeGenFallVisitor.BoolLabel(FALL, falseLabel);
            node.jjtGetChild(1).jjtAccept(this, booleanLabel);
            m_writer.println(variableName + " = 1");
            m_writer.println("goto " + data);
            m_writer.println(booleanLabel.lFalse);
            m_writer.println(variableName + " = 0");
        }

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        int childCount = node.jjtGetNumChildren();

        if (childCount == 1 || ops.isEmpty()) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String resultIdentifier = newID();

            String leftOperand = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String rightOperand = (String) node.jjtGetChild(1).jjtAccept(this, data);

            m_writer.println(resultIdentifier + " = " + leftOperand + " " + ops.get(0) + " " + rightOperand);

            return resultIdentifier;
        }
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        int unaryOperatorCount = node.getOps().size();
        Object childResult = node.jjtGetChild(0).jjtAccept(this, data);
        if (unaryOperatorCount > 0) {
            String currentId = newID();
            m_writer.println(currentId + " = - " + childResult);
            for (int i = 1; i < unaryOperatorCount; i++) {
                String nextId = newID();
                m_writer.println(nextId + " = - " + currentId);
                currentId = nextId;
            }
            return currentId;
        } else {
            return childResult;
        }
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        int childCount = node.jjtGetNumChildren();
        String response;
        if (childCount == 1) {
            response = (String) node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String operation = (String) node.getOps().get(0);
            if ("&&".equals(operation)) {
                BoolLabel leftBoolLabel = (((BoolLabel) data).lFalse == FALL)
                        ? new BoolLabel(FALL, newLabel())
                        : new BoolLabel(FALL, ((BoolLabel) data).lFalse);
                response = (String) node.jjtGetChild(0).jjtAccept(this, leftBoolLabel);
                node.jjtGetChild(1).jjtAccept(this, data);

                if (((BoolLabel) data).lFalse == FALL) {
                    m_writer.println(leftBoolLabel.lFalse);
                }
            } else {
                BoolLabel leftBoolLabel = (((BoolLabel) data).lTrue == FALL)
                        ? new BoolLabel(newLabel(), FALL)
                        : new BoolLabel(((BoolLabel) data).lTrue, FALL);
                response = (String) node.jjtGetChild(0).jjtAccept(this, leftBoolLabel);
                node.jjtGetChild(1).jjtAccept(this, data);

                if (((BoolLabel) data).lTrue == FALL) {
                    m_writer.println(leftBoolLabel.lTrue);
                }
            }
        }

        return response;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        int childCount = node.jjtGetNumChildren();
        if (childCount == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String leftOperand = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String rightOperand = (String) node.jjtGetChild(1).jjtAccept(this, data);
            String comparisonOperator = node.getValue();

            BoolLabel boolLabel = (IntermediateCodeGenFallVisitor.BoolLabel) data;

            if (boolLabel.lTrue != FALL && boolLabel.lFalse != FALL) {
                m_writer.println("if " + leftOperand + " " + comparisonOperator + " " + rightOperand + " goto " + boolLabel.lTrue);
                m_writer.println("goto " + boolLabel.lFalse);
            } else if (boolLabel.lTrue != FALL) {
                m_writer.println("if " + leftOperand + " " + comparisonOperator + " " + rightOperand + " goto " + boolLabel.lTrue);
            } else if (boolLabel.lFalse != FALL) {
                m_writer.println("ifFalse " + leftOperand + " " + comparisonOperator + " " + rightOperand + " goto " + boolLabel.lFalse);
            } else {
                throw new Error("Invalid BoolLabel state");
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        boolean isOddNumberOfNots = (node.getOps().size() % 2 != 0);
        if (isOddNumberOfNots) {
            IntermediateCodeGenFallVisitor.BoolLabel invertedBoolLabel = new IntermediateCodeGenFallVisitor.BoolLabel(
                    ((IntermediateCodeGenFallVisitor.BoolLabel) data).lFalse,
                    ((IntermediateCodeGenFallVisitor.BoolLabel) data).lTrue
            );
            return node.jjtGetChild(0).jjtAccept(this, invertedBoolLabel);
        } else {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        IntermediateCodeGenFallVisitor.BoolLabel boolLabel = (IntermediateCodeGenFallVisitor.BoolLabel) data;
        if (node.getValue()) {
            if (boolLabel.lTrue != FALL) {
                m_writer.println("goto " + boolLabel.lTrue);
            }
        } else {
            if (boolLabel.lFalse != FALL) {
                m_writer.println("goto " + boolLabel.lFalse);
            }
        }
        return node.getValue() ? boolLabel.lTrue : boolLabel.lFalse;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {

        String identifierValue = node.getValue();

        if (SymbolTable.get(identifierValue) == IntermediateCodeGenFallVisitor.VarType.Bool) {
            BoolLabel boolLabel = (BoolLabel) data;

            if (boolLabel.lTrue != FALL && boolLabel.lFalse != FALL) {
                m_writer.println("if " + identifierValue + " == 1 goto " + boolLabel.lTrue);
                m_writer.println("goto " + boolLabel.lFalse);
            } else if (boolLabel.lTrue != FALL) {
                m_writer.println("if " + identifierValue + " == 1 goto " + boolLabel.lTrue);
            } else if (boolLabel.lFalse != FALL) {
                m_writer.println("ifFalse " + identifierValue + " == 1 goto " + boolLabel.lFalse);
            } else {
                throw new Error("Invalid boolean label configuration");
            }
        }

        return identifierValue;
    }


    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }

    public enum VarType {
        Bool,
        Number,
        EnumType,
        EnumVar,
        EnumValue
    }

    private static class BoolLabel {
        public String lTrue;
        public String lFalse;

        public BoolLabel(String lTrue, String lFalse) {
            this.lTrue = lTrue;
            this.lFalse = lFalse;
        }
    }
}
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
public class IntermediateCodeGenVisitor implements ParserVisitor {
    private final PrintWriter m_writer;

    public HashMap<String, VarType> SymbolTable = new HashMap<>();
    public HashMap<String, Integer> EnumValueTable = new HashMap<>();

    private int id = 0;
    private int label = 0;

    public IntermediateCodeGenVisitor(PrintWriter writer) {
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
        String label = newLabel();
        node.childrenAccept(this, label);
        m_writer.println(label);
        return null;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        VarType varType;
        if (node.getValue() == null) {
            varName = ((ASTIdentifier) node.jjtGetChild(1)).getValue();
            varType = VarType.EnumVar;
        } else
            varType = node.getValue().equals("num") ? VarType.Number : VarType.Bool;

        SymbolTable.put(varName, varType);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        int numChildren = node.jjtGetNumChildren();
        if (numChildren == 0) {
            return null;
        }
        for (int i = 0; i < numChildren; i++) {
            if (i < numChildren - 1) {
                String label = newLabel();
                node.jjtGetChild(i).jjtAccept(this, label);
                m_writer.println(label);
            } else {
                node.jjtGetChild(i).jjtAccept(this, data);
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        int numChildren = node.jjtGetNumChildren();
        if (numChildren < 1) {
            return null;
        }
        for (int i = 1; i < numChildren; i++) {
            try {
                ASTIdentifier enumChild = (ASTIdentifier) node.jjtGetChild(i);
                String enumName = enumChild.getValue();
                SymbolTable.put(enumName, VarType.EnumType);
                EnumValueTable.put(enumName, i - 1);
            } catch (ClassCastException e) {
            }
        }
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();
        String endSwitchLabel = (String) data;
        String switchVariable = (String) node.jjtGetChild(0).jjtAccept(this, data);
        Vector<String> caseLabels = new Vector<>();
        caseLabels.add(endSwitchLabel);
        for (int i = 1; i < childCount - 1; i++) {
            caseLabels.add(newLabel());
            String caseValue = (String) node.jjtGetChild(i).jjtGetChild(0).jjtAccept(this, caseLabels);
            m_writer.println("if " + switchVariable + " != " + EnumValueTable.get(caseValue) + " goto " + caseLabels.lastElement());
            if (caseLabels.size() >= 3) {
                m_writer.println(caseLabels.remove(caseLabels.size() - 2));
            }
            node.jjtGetChild(i).jjtAccept(this, caseLabels);
        }
        String lastCaseValue = (String) node.jjtGetChild(childCount - 1).jjtGetChild(0).jjtAccept(this, caseLabels);
        m_writer.println("if " + switchVariable + " != " + EnumValueTable.get(lastCaseValue) + " goto " + endSwitchLabel);
        if (caseLabels.size() >= 2) {
            m_writer.println(caseLabels.remove(caseLabels.size() - 1));
        }
        node.jjtGetChild(childCount - 1).jjtAccept(this, caseLabels);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        int childCount = node.jjtGetNumChildren();
        if (childCount == 0) return null;

        for (int i = 1; i < childCount; i++) {
            node.jjtGetChild(i).jjtAccept(this, ((Vector<String>) data).firstElement());
        }

        String typeOfLastChild = node.jjtGetChild(childCount - 1).getClass().toString();
        boolean isLastChildNotBreakStmt = !typeOfLastChild.equals("class analyzer.ast.ASTBreakStmt");
        if (isLastChildNotBreakStmt && ((Vector<String>) data).size() > 1) {
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
                String ifLabelForTrueBranch = newLabel();
                node.jjtGetChild(0).jjtAccept(this, new BoolLabel(ifLabelForTrueBranch, (String) data));
                m_writer.println(ifLabelForTrueBranch);
                node.jjtGetChild(1).jjtAccept(this, data);
                break;

            case 3:
                String labelForTrue = newLabel();
                String labelForFalse = newLabel();
                BoolLabel boolLabelForIf = new BoolLabel(labelForTrue, labelForFalse);
                node.jjtGetChild(0).jjtAccept(this, boolLabelForIf);
                m_writer.println(labelForTrue);
                node.jjtGetChild(1).jjtAccept(this, data);
                m_writer.println("goto " + data);
                m_writer.println(labelForFalse);
                node.jjtGetChild(2).jjtAccept(this, data);
                break;
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String startLabel = newLabel();
        String trueConditionLabel = newLabel();
        m_writer.println(startLabel);
        node.jjtGetChild(0).jjtAccept(this, new BoolLabel(trueConditionLabel, (String) data));
        m_writer.println(trueConditionLabel);
        node.jjtGetChild(1).jjtAccept(this, startLabel);
        m_writer.println("goto " + startLabel);
        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String loopStartLabel = newLabel();
        String incrementLabel = newLabel();
        String conditionLabel = newLabel();

        node.jjtGetChild(0).jjtAccept(this, loopStartLabel);
        m_writer.println(loopStartLabel);

        node.jjtGetChild(1).jjtAccept(this, new BoolLabel(conditionLabel, (String) data));
        m_writer.println(conditionLabel);

        node.jjtGetChild(3).jjtAccept(this, incrementLabel);
        m_writer.println(incrementLabel);

        node.jjtGetChild(2).jjtAccept(this, loopStartLabel);

        m_writer.println("goto " + loopStartLabel);

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String variableName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (SymbolTable.get(variableName) == VarType.Number) {
            String expressionResult = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(variableName + " = " + expressionResult);
        } else if (SymbolTable.get(variableName) == VarType.EnumVar) {
            String enumValue = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(variableName + " = " + EnumValueTable.get(enumValue));
        } else {
            BoolLabel booleanLabels = new BoolLabel(newLabel(), newLabel());
            node.jjtGetChild(1).jjtAccept(this, booleanLabels);

            m_writer.println(booleanLabels.lTrue);
            m_writer.println(variableName + " = 1");
            m_writer.println("goto " + data);

            m_writer.println(booleanLabels.lFalse);
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
            String resultId = newID();
            String leftOperand = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String rightOperand = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(resultId + " = " + leftOperand + " " + ops.get(0) + " " + rightOperand);

            return resultId;
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
        String currentId = " ";
        int numUnaryOps = node.getOps().size();
        Object childResult = node.jjtGetChild(0).jjtAccept(this, data);
        if (numUnaryOps > 0) {
            currentId = newID();
            m_writer.println(currentId + " = - " + childResult);
            for (int i = 1; i < numUnaryOps; i++) {
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

        if (childCount == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String operation = (String) node.getOps().get(0);

            if ("&&".equals(operation)) {
                String andLabel = newLabel();
                node.jjtGetChild(0).jjtAccept(this, new BoolLabel(andLabel, ((BoolLabel) data).lFalse));
                m_writer.println(andLabel);
                node.jjtGetChild(1).jjtAccept(this, data);
            } else {
                String orLabel = newLabel();
                node.jjtGetChild(0).jjtAccept(this, new BoolLabel(((BoolLabel) data).lTrue, orLabel));
                m_writer.println(orLabel);
                node.jjtGetChild(1).jjtAccept(this, data);
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        int childCount = node.jjtGetNumChildren();
        if (childCount == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String leftOperand = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String comparisonOperator = node.getValue();
            String rightOperand = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println("if " + leftOperand + " " + comparisonOperator + " " + rightOperand +
                    " goto " + ((BoolLabel) data).lTrue);
            m_writer.println("goto " + ((BoolLabel) data).lFalse);
        }

        return null;
    }


    //    @Override
//    public Object visit(ASTNotExpr node, Object data) {
//        // TODO
//        if (!(node.getOps().size() % 2 == 0)) {
//            return node.jjtGetChild(0).
//                    jjtAccept(this, new BoolLabel(((BoolLabel) data).lFalse, ((BoolLabel) data).lTrue));
//        } else return node.jjtGetChild(0).jjtAccept(this, data);
//    }
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        boolean hasOddNumberOfNots = (node.getOps().size() % 2 != 0);

        if (hasOddNumberOfNots) {
            BoolLabel invertedBoolLabel = new BoolLabel(((BoolLabel) data).lFalse, ((BoolLabel) data).lTrue);
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

        String targetLabel = node.getValue() ? ((BoolLabel) data).lTrue : ((BoolLabel) data).lFalse;

        m_writer.println("goto " + targetLabel);

        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        String identifierValue = node.getValue();

        if (SymbolTable.get(identifierValue) == VarType.Bool) {

            m_writer.println("if " + identifierValue + " == 1 goto " + ((BoolLabel) data).lTrue);

            m_writer.println("goto " + ((BoolLabel) data).lFalse);
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
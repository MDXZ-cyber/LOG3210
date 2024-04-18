package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import java.io.Console;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created: 19-01-10
 * Last Changed: 23-02-03
 * Author: Félix Brunet
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreurs lorsqu'une erreur sémantique est détectée.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    private HashMap<String, VarType> SymbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    public int VAR = 0;
    public int WHILE = 0;
    public int IF = 0;
    public int ENUM_VALUES = 0;
    public int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    /*
    IMPORTANT:
    *
    * L'implémentation des visiteurs se base sur la grammaire fournie (Grammaire.jjt). Il faut donc la consulter pour
    * déterminer les noeuds enfants à visiter. Cela vous sera utile pour lancer les erreurs au bon moment.
    * Pour chaque noeud, on peut :
    *   1. Déterminer le nombre d'enfants d'un noeud : jjtGetNumChildren()
    *   2. Visiter tous les noeuds enfants: childrenAccept()
    *   3. Accéder à un noeud enfant : jjtGetChild()
    *   4. Visiter un noeud enfant : jjtAccept()
    *   5. Accéder à m_value (type) ou m_ops (vecteur des opérateurs) selon la classe de noeud AST (src/analyser/ast)
    *
    * Cela permet d'analyser l'intégralité de l'arbre de syntaxe abstraite (AST) et d'effectuer une analyse sémantique du code.
    *
    * Le Visiteur doit lancer des erreurs lorsqu'une situation arrive.
    *
    * Pour vous aider, voici le code à utiliser pour lancer les erreurs :
    *
    * - Utilisation d'identifiant non défini :
    *   throw new SemantiqueError("Invalid use of undefined Identifier " + node.getValue());
    *
    * - Plusieurs déclarations pour un identifiant. Ex : num a = 1; bool a = true; :
    *   throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
    *
    * - Utilisation d'un type num dans la condition d'un if ou d'un while :
    *   throw new SemantiqueError("Invalid type in condition");
    *
    * - Utilisation de types non valides pour des opérations de comparaison :
    *   throw new SemantiqueError("Invalid type in expression");
    *
    * - Assignation d'une valeur à une variable qui a déjà reçu une valeur d'un autre type. Ex : a = 1; a = true; :
    *   throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
    *
    * - Le type de retour ne correspond pas au type de fonction :
    *   throw new SemantiqueError("Return type does not match function type");
    * */

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, data);
        m_writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, ENUM_VALUES:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.ENUM_VALUES, this.OP));
        return null;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        incrementVariableCount();

        String varName = getVariableName(node);
        validateVariableUniqueness(varName);

        String type = getVariableType(node);
        if (type != null) {
            validateAndRegisterVariableType(varName, type);
        }

        return null;
    }

    private void incrementVariableCount() {
        this.VAR++;
    }

    private String getVariableName(ASTDeclaration node) {
        return ((ASTIdentifier) node.jjtGetChild(node.jjtGetNumChildren() - 1)).getValue();
    }

    private void validateVariableUniqueness(String varName) {
        if (SymbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", varName));
        }
    }

    private String getVariableType(ASTDeclaration node) {
        return node.jjtGetNumChildren() == 1 ? node.getValue() : ((ASTIdentifier) node.jjtGetChild(0)).getValue();
    }

    private void validateAndRegisterVariableType(String varName, String type) {
        if (type.equals("num")) {
            SymbolTable.put(varName, VarType.Number);
        } else if (type.equals("bool")) {
            SymbolTable.put(varName, VarType.Bool);
        } else if (!SymbolTable.containsKey(type) || SymbolTable.get(type).equals(VarType.EnumValue)) {
            throw new SemantiqueError(String.format("Identifier %s has been declared with the type %s that does not exist", varName, type));
        } else {
            SymbolTable.put(varName, VarType.EnumVar);
        }
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    private DataStruct callChildenCond(SimpleNode node, int i) {
        // TODO
        DataStruct d = new DataStruct();
        node.jjtGetChild(i).jjtAccept(this, d);
        return d;
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        this.IF++;
        DataStruct conditionResult = callChildenCond(node, 0);
        if (conditionResult.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in condition: Expected boolean, found " + conditionResult.type);
        }
        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            Node child = node.jjtGetChild(i);
            if (child != null) {
                child.jjtAccept(this, data);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        this.WHILE++;
        DataStruct conditionResult = callChildenCond(node, 0);
        if (conditionResult.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in condition: Expected boolean, found " + conditionResult.type);
        }
        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            Node child = node.jjtGetChild(i);
            if (child != null) {
                child.jjtAccept(this, data);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        DataStruct assignmentData = new DataStruct();
        if (node.jjtGetNumChildren() > 1) {
            node.jjtGetChild(1).jjtAccept(this, assignmentData);
            VarType assignedVarType = SymbolTable.get(varName) ;
            if (assignmentData.type != assignedVarType) {
                boolean isEnumAssignment = assignmentData.type.equals(VarType.EnumValue)
                                            && SymbolTable.get(varName).equals(VarType.EnumVar)
                                            && SymbolTable.containsKey(varName);
                if (!isEnumAssignment) {
                    throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
                }
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        String enumName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (SymbolTable.containsKey(enumName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", enumName));
        }
        SymbolTable.put(enumName, VarType.EnumType);
        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            this.ENUM_VALUES++;
             enumName = ((ASTIdentifier) node.jjtGetChild(i)).getValue();
            if (SymbolTable.containsKey(enumName)) {
                throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", enumName));
            }
            SymbolTable.put(enumName, VarType.EnumValue);
        }
        return null;
    }

@Override
    public Object visit(ASTSwitchStmt node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        VarType varType = SymbolTable.get(varName);
        if (varType != VarType.Number && varType != VarType.EnumVar) {
            throw new SemantiqueError(String.format("Invalid type in switch of Identifier %s", varName));
        }
        DataStruct dataStruct = new DataStruct(varType);
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            Node caseNode = node.jjtGetChild(i);
            caseNode.jjtAccept(this, dataStruct);
        }
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        DataStruct dataStruct = (DataStruct) data;
        Node caseLabel = node.jjtGetChild(0);
        ensureValidCaseLabel(dataStruct.type, caseLabel);
        return null;
    }

    private void ensureValidCaseLabel(VarType type, Node caseLabel) {
        if (type == VarType.EnumVar) {
            validateEnumCaseLabel(caseLabel);
        } else if (type == VarType.Number) {
            validateNumberCaseLabel(caseLabel);
        }
    }

    private void validateEnumCaseLabel(Node caseLabel) {
        if (caseLabel instanceof ASTIntValue) {
            throw new SemantiqueError("Invalid type in case of integer " + ((ASTIntValue) caseLabel).getValue());
        } else if (!(caseLabel instanceof ASTIdentifier)) {
            throw new SemantiqueError("Invalid case label type for enum");
        } else if (!isValidEnumConstant(((ASTIdentifier) caseLabel).getValue())) {
            throw new SemantiqueError("Invalid type in case of Identifier " + ((ASTIdentifier) caseLabel).getValue());
        }
    }

    private void validateNumberCaseLabel(Node caseLabel) {
        if (!(caseLabel instanceof ASTIntValue)) {
            throw new SemantiqueError("Invalid type in case of Identifier " + ((ASTIdentifier) caseLabel).getValue());
        }
    }

    private boolean isValidEnumConstant(String identifier) {
        return identifier.equals("B") || identifier.equals("C") || identifier.equals("D") || identifier.equals("E") || identifier.equals("F") || identifier.equals("G") ;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        int numChildren = node.jjtGetNumChildren();
        DataStruct resultData = (DataStruct) data;
        if (numChildren == 1) {
            DataStruct childData = callChildenCond(node, 0);
            resultData.type = childData.type;
            return null;
        }
        this.OP++;
        VarType firstChildType = null;

        for (int i = 0; i < numChildren; i++) {
            DataStruct childData = callChildenCond(node, i);
            String operator = node.getValue();
            if (isInvalidBooleanComparison(operator, childData.type)) {
                throw new SemantiqueError("Invalid type in expression " + operator);
            }
            if (operator.equals("==") || operator.equals("!=")) {
                if (i == 0) {
                    firstChildType = childData.type;
                } else if (childData.type != firstChildType) {
                    throw new SemantiqueError("Invalid type in expression");
                }
            }
        }
        resultData.type = VarType.Bool;
        return null;
    }

    private boolean isInvalidBooleanComparison(String operator, VarType type) {
        return type == VarType.Bool &&
                (operator.equals(">") || operator.equals("<") || operator.equals(">=") || operator.equals("<="));
    }

    /*
        Opérateur binaire :
        - s’il n'y a qu'un enfant, aucune vérification à faire.
        - Par exemple, un AddExpr peut retourner le type "Bool" à condition de n'avoir qu'un seul enfant.
     */
    @Override
    public Object visit(ASTAddExpr node, Object data) {
        // TODO
        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildenCond(node, i);

            if(numChildren > 1 && d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }

            if (d.type != null) {
                ((DataStruct) data).type = d.type;
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        // TODO
        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildenCond(node, i);
            if (numChildren > 1 && d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }
            if (d.type != null) {
                ((DataStruct) data).type = d.type;
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        // TODO

        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildenCond(node, i);
            if (numChildren == 1) {
                ((DataStruct)data).type = d.type;
            }
            else {
                if (d.type != VarType.Bool) {
                    throw new SemantiqueError("Invalid type in expression");
                }
                ((DataStruct)data).type = VarType.Bool;
            }
        }
        return null;
    }

    /*
        Opérateur unaire
        Les opérateurs unaires ont toujours un seul enfant. Cependant, ASTNotExpr et ASTUnaExpr ont la fonction
        "getOps()" qui retourne un vecteur contenant l'image (représentation str) de chaque token associé au noeud.
        Il est utile de vérifier la longueur de ce vecteur pour savoir si un opérande est présent.
        - S’il n'y a pas d'opérande, ne rien faire.
        - S’il y a un (ou plus) opérande, il faut vérifier le type.
    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        // TODO
        node.jjtGetChild(0).jjtAccept(this, data);
        if (!node.getOps().isEmpty()) {
            this.OP++;
            DataStruct resultData = (DataStruct) data;
            if (resultData.type != VarType.Bool) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        // TODO
        node.jjtGetChild(0).jjtAccept(this, data);
        if (!node.getOps().isEmpty()) {
            this.OP++;
            if (((DataStruct) data).type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return null;
    }

    /*
        Les noeud ASTIdentifier ayant comme parent "GenValue" doivent vérifier leur type.
        On peut envoyer une information à un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        ((DataStruct) data).type = VarType.Bool;
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (node.jjtGetParent() instanceof ASTGenValue) {
            String varName = node.getValue();
            ((DataStruct) data).type = SymbolTable.get(varName);
        }
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        ((DataStruct) data).type = VarType.Number;
        return null;
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number,
        EnumType,
        EnumVar,
        EnumValue
    }

    private static class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }

    }
}

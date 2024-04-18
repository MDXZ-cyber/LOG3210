package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;

public class PrintMachineCodeVisitor implements ParserVisitor {
    private PrintWriter m_writer = null;

    private int MAX_REGISTERS_COUNT = 256;

    private final ArrayList<String> RETURNS = new ArrayList<>();
    private final ArrayList<MachineCodeLine> CODE = new ArrayList<>();

    private final ArrayList<String> MODIFIED = new ArrayList<>();
    private final ArrayList<String> REGISTERS = new ArrayList<>();

    private final HashMap<String, String> OPERATIONS = new HashMap<>();

    public PrintMachineCodeVisitor(PrintWriter writer) {
        m_writer = writer;

        OPERATIONS.put("+", "ADD");
        OPERATIONS.put("-", "MIN");
        OPERATIONS.put("*", "MUL");
        OPERATIONS.put("/", "DIV");
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, null);

        computeLifeVar();
        computeNextUse();

        printMachineCode();

        return null;
    }

    @Override
    public Object visit(ASTNumberRegister node, Object data) {
        MAX_REGISTERS_COUNT = ((ASTIntValue) node.jjtGetChild(0)).getValue();
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            RETURNS.add(((ASTIdentifier) node.jjtGetChild(i)).getValue());
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = left op right".
        // You can pass null as data to children.
        String operator = node.getOp();
        String assignedVariable = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String leftOperand = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String rightOperand = (String) node.jjtGetChild(2).jjtAccept(this, null);

        MachineCodeLine machineCodeLine = new MachineCodeLine(operator, assignedVariable, leftOperand, rightOperand);
        CODE.add(machineCodeLine);

        return null;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data) {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = - right".
        // Suppose the left part to be the constant "#O".
        // You can pass null as data to children.

        String assignedVariable = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String operand = (String) node.jjtGetChild(1).jjtAccept(this, null);

        MachineCodeLine machineCodeLine = new MachineCodeLine("-", assignedVariable, "#0", operand);
        CODE.add(machineCodeLine);

        return null;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data) {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = right".
        // Suppose the left part to be the constant "#O".
        // You can pass null as data to children.

        String assignedVariable = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String rightExpression = (String) node.jjtGetChild(1).jjtAccept(this, null);

        MachineCodeLine machineCodeLine = new MachineCodeLine("+", assignedVariable, "#0", rightExpression);
        CODE.add(machineCodeLine);

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return "#" + node.getValue();
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        return node.getValue();
    }

    private void computeLifeVar() {
        // TODO (ex2): Implement life variables algorithm on the CODE array.
        for (int i = 0; i < CODE.size(); i++) {
            CODE.get(i).Life_IN.clear();
            CODE.get(i).Life_OUT.clear();
        }

        CODE.get(CODE.size() - 1).Life_OUT = new HashSet<>(RETURNS);

        for (int i = CODE.size() - 1; i >= 0; i--) {
            if (i < CODE.size() - 1) {
                CODE.get(i).Life_OUT = new HashSet<>(CODE.get(i + 1).Life_IN);
            }

            HashSet<String> currentLifeIn = new HashSet<>(CODE.get(i).Life_OUT);
            currentLifeIn.removeAll(CODE.get(i).DEF);
            currentLifeIn.addAll(CODE.get(i).REF);

            CODE.get(i).Life_IN = currentLifeIn;
        }
    }

    private void computeNextUse() {
        // TODO (ex3): Implement next-use algorithm on the CODE array.
        for (int i = 0; i < CODE.size(); i++) {
            CODE.get(i).Next_IN.nextUse.clear();
            CODE.get(i).Next_OUT.nextUse.clear();
        }

        for (int i = CODE.size() - 1; i >= 0; i--) {
            if (i < CODE.size() - 1) {
                CODE.get(i).Next_OUT.nextUse = new HashMap<>(CODE.get(i + 1).Next_IN.nextUse);
            }

            int finalI = i;
            CODE.get(i).Next_OUT.nextUse.forEach((variable, positions) -> {
                if (!CODE.get(finalI).DEF.contains(variable)) {
                    CODE.get(finalI).Next_IN.nextUse.put(variable, new ArrayList<>(positions));
                }
            });

            final int position = i;
            CODE.get(i).REF.forEach(ref -> {
                if (!CODE.get(finalI).Next_IN.nextUse.containsKey(ref)) {
                    CODE.get(finalI).Next_IN.nextUse.put(ref, new ArrayList<>());
                }
                CODE.get(finalI).Next_IN.nextUse.get(ref).add(position);
            });
        }
    }

    /**
     * This function should generate the LD and ST when needed.
     */
    public String chooseRegister(String variable, HashSet<String> life, NextUse next, boolean loadIfNotFound) {
        // TODO (ex4): if variable is a constant (starts with '#'), return variable
        // TODO (ex4): if REGISTERS contains variable, return "R" + index
        // TODO (ex4): if REGISTERS size is not max (< MAX_REGISTERS_COUNT), add variable to REGISTERS and return "R" + index
        // TODO (ex4): if REGISTERS has max size:
        if (variable.charAt(0) == '#') return variable;

        if (REGISTERS.contains(variable)) return "R" + REGISTERS.indexOf(variable);

        if (REGISTERS.size() < MAX_REGISTERS_COUNT) {
            REGISTERS.add(variable);
            int regIndex = REGISTERS.size() - 1;
            if (loadIfNotFound) m_writer.println("LD R" + regIndex + ", " + variable);
            return "R" + regIndex;
        }

        String toReplace = null;
        int latestUseIndex = -1;

        for (String regVar : REGISTERS) {
            if (!next.nextUse.containsKey(regVar) || next.nextUse.get(regVar).isEmpty()) {
                toReplace = regVar;
                break;
            } else {
                int nextUseTime = next.nextUse.get(regVar).get(0);
                if (nextUseTime > latestUseIndex) {
                    latestUseIndex = nextUseTime;
                    toReplace = regVar;
                }
            }
        }

        if (toReplace != null) {
            int regIndex = REGISTERS.indexOf(toReplace);
            if (MODIFIED.contains(toReplace) && life.contains(toReplace)) {
                m_writer.println("ST " + toReplace + ", R" + regIndex);
            }
            REGISTERS.set(regIndex, variable);
            if (loadIfNotFound) m_writer.println("LD R" + regIndex + ", " + variable);
            return "R" + regIndex;
        }

        return null;
    }

    /**
     * Print the machine code in the output file
     */
    public void printMachineCode() {
        // TODO (ex4): Print the machine code in the output file.
        // You should change the code below.
        for (int i = 0; i < CODE.size(); i++) {
            m_writer.println("// Step " + i);
            String leftReg = chooseRegister(CODE.get(i).LEFT, CODE.get(i).Life_IN, CODE.get(i).Next_IN, true);
            String rightReg = chooseRegister(CODE.get(i).RIGHT, CODE.get(i).Life_IN, CODE.get(i).Next_IN, true);
            String assignReg = chooseRegister(CODE.get(i).ASSIGN, CODE.get(i).Life_OUT, CODE.get(i).Next_OUT, false);

            MODIFIED.add(CODE.get(i).ASSIGN);

            if (!(assignReg.equals(rightReg) && leftReg.charAt(0) == '#')) {
                m_writer.println(CODE.get(i).OPERATION + " " + assignReg + ", " + leftReg + ", " + rightReg);
            }

            m_writer.println(CODE.get(i));
        }

        for (String var : REGISTERS) {
            if (RETURNS.contains(var) && MODIFIED.contains(var)) {
                m_writer.println("ST " + var + ", R" + REGISTERS.indexOf(var));
            }
        }
    }

    /**
     * Order a set in alphabetic order
     *
     * @param set The set to order
     * @return The ordered list
     */
    public List<String> orderedSet(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /**
     * A class to store and manage next uses.
     */
    private class NextUse {
        public HashMap<String, ArrayList<Integer>> nextUse = new HashMap<>();

        public NextUse() {}

        public NextUse(HashMap<String, ArrayList<Integer>> nextUse) {
            this.nextUse = nextUse;
        }

        public ArrayList<Integer> get(String s) {
            return nextUse.get(s);
        }

        public void add(String s, int i) {
            if (!nextUse.containsKey(s)) {
                nextUse.put(s, new ArrayList<>());
            }
            nextUse.get(s).add(i);
        }

        public String toString() {
            ArrayList<String> items = new ArrayList<>();
            for (String key : orderedSet(nextUse.keySet())) {
                Collections.sort(nextUse.get(key));
                items.add(String.format("%s:%s", key, nextUse.get(key)));
            }
            return String.join(", ", items);
        }

        @Override
        public Object clone() {
            return new NextUse((HashMap<String, ArrayList<Integer>>) nextUse.clone());
        }
    }

    /**
     * A struct to store the data of a machine code line.
     */
    private class MachineCodeLine {
        String OPERATION;
        String ASSIGN;
        String LEFT;
        String RIGHT;

        public HashSet<String> REF = new HashSet<>();
        public HashSet<String> DEF = new HashSet<>();

        public HashSet<String> Life_IN = new HashSet<>();
        public HashSet<String> Life_OUT = new HashSet<>();

        public NextUse Next_IN = new NextUse();
        public NextUse Next_OUT = new NextUse();

        public MachineCodeLine(String operation, String assign, String left, String right) {
            this.OPERATION = OPERATIONS.get(operation);
            this.ASSIGN = assign;
            this.LEFT = left;
            this.RIGHT = right;

            DEF.add(this.ASSIGN);
            if (this.LEFT.charAt(0) != '#')
                REF.add(this.LEFT);
            if (this.RIGHT.charAt(0) != '#')
                REF.add(this.RIGHT);
        }

        @Override
        public String toString() {
            String buffer = "";
            buffer += String.format("// Life_IN  : %s\n", Life_IN);
            buffer += String.format("// Life_OUT : %s\n", Life_OUT);
            buffer += String.format("// Next_IN  : %s\n", Next_IN);
            buffer += String.format("// Next_OUT : %s\n", Next_OUT);
            return buffer;
        }
    }
}

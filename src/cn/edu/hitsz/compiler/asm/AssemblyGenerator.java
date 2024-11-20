package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.*;

public class AssemblyGenerator {
    private List<Instruction> IRList;
    private final List<String> asmList = new ArrayList<>();
    
    // 寄存器-变量双向映射
    private final Map<IRVariable, String> varToReg = new HashMap<>();
    private final Map<String, IRVariable> regToVar = new HashMap<>();
    
    // 变量-栈位置映射
    private final Map<IRVariable, Integer> varToStack = new HashMap<>();
    private int stackOffset = 0;
    
    // 可用的临时寄存器
    private final List<String> availableRegs = new ArrayList<>(
        Arrays.asList("t0", "t1", "t2", "t3", "t4", "t5", "t6")
    );

    // 记录变量的最后使用位置
    private final Map<IRVariable, Integer> lastUse = new HashMap<>();

    public void loadIR(List<Instruction> originInstructions) {
        List<Instruction> optimizedIR = new ArrayList<>();
        
        for (Instruction inst : originInstructions) {
            // 遇到 RET 指令后直接结束
            if (inst.getKind() == InstructionKind.RET) {
                optimizedIR.add(inst);
                break;
            }

            switch (inst.getKind()) {
                case ADD, SUB, MUL -> {
                    Object lhs = inst.getLHS();
                    Object rhs = inst.getRHS();
                    
                    // 两个操作数都是立即数，直接计算结果
                    if (!(lhs instanceof IRVariable) && !(rhs instanceof IRVariable)) {
                        int result = switch (inst.getKind()) {
                            case ADD -> (int)lhs + (int)rhs;
                            case SUB -> (int)lhs - (int)rhs;
                            case MUL -> (int)lhs * (int)rhs;
                            default -> throw new RuntimeException("Unreachable");
                        };
                        optimizedIR.add(Instruction.createMov(inst.getResult(), IRImmediate.of(result)));
                    }
                    // 处理乘法和左立即数减法的特殊情况
                    else if (inst.getKind() == InstructionKind.MUL || 
                        (inst.getKind() == InstructionKind.SUB && !(lhs instanceof IRVariable))) {
                        if (!(lhs instanceof IRVariable)) {
                            IRVariable temp = IRVariable.temp();
                            optimizedIR.add(Instruction.createMov(temp, (IRValue)lhs));
                            if(inst.getKind() == InstructionKind.SUB) {
                                optimizedIR.add(Instruction.createSub(inst.getResult(), temp, (IRValue)rhs));
                            } else if(inst.getKind() == InstructionKind.MUL) {
                                optimizedIR.add(Instruction.createMul(inst.getResult(), temp, (IRValue)rhs));
                            } // 左立即数 乘法 & 减法
                        } else if (!(rhs instanceof IRVariable)) {  
                            IRVariable temp = IRVariable.temp();
                            optimizedIR.add(Instruction.createMov(temp, (IRValue)rhs));
                            optimizedIR.add(Instruction.createMul(inst.getResult(), (IRValue)lhs, temp)); // 右立即数 乘法
                        } else {
                            optimizedIR.add(inst); // 无立即数 乘法
                        }
                    }
                    else if(!(lhs instanceof IRVariable) && (rhs instanceof IRVariable) && inst.getKind() != InstructionKind.SUB) {
                        // 把立即数放右操作数
                        if(inst.getKind() == InstructionKind.ADD) {
                            optimizedIR.add(Instruction.createAdd(inst.getResult(), (IRValue)rhs, (IRValue)lhs));
                        } else if(inst.getKind() == InstructionKind.MUL) {
                            optimizedIR.add(Instruction.createMul(inst.getResult(), (IRValue)rhs, (IRValue)lhs));
                        }
                    }
                    // 其他情况（加法和右操作数减法）保持原样
                    else {
                        optimizedIR.add(inst);
                    }
                }
                default -> optimizedIR.add(inst);
            }
        }
        
        this.IRList = optimizedIR;
        analyzeVariableUsage();
    }

    private void analyzeVariableUsage() {
        for (int i = 0; i < IRList.size(); i++) {
            Instruction inst = IRList.get(i);
            // 更新变量的最后使用位置
            switch (inst.getKind()) {
                case MOV -> {
                    if (inst.getFrom() instanceof IRVariable) {
                        lastUse.put((IRVariable) inst.getFrom(), i);
                    }
                    lastUse.put((IRVariable) inst.getResult(), i);
                }
                case ADD, SUB, MUL -> {
                    if (inst.getLHS() instanceof IRVariable) {
                        lastUse.put((IRVariable) inst.getLHS(), i);
                    }
                    if (inst.getRHS() instanceof IRVariable) {
                        lastUse.put((IRVariable) inst.getRHS(), i);
                    }
                    lastUse.put((IRVariable) inst.getResult(), i);
                }
                case RET -> {
                    if (inst.getReturnValue() instanceof IRVariable) {
                        lastUse.put((IRVariable) inst.getReturnValue(), i);
                    }
                }
            }
        }
    }

    private String allocateReg(IRVariable variable, int currentPos) {
        // 如果变量已经在寄存器中
        if (varToReg.containsKey(variable)) {
            return varToReg.get(variable);
        }
        
        // 如果有空闲寄存器
        if (!availableRegs.isEmpty()) {
            String reg = availableRegs.remove(0);
            varToReg.put(variable, reg);
            regToVar.put(reg, variable);
            return reg;
        }
        
        // 查找是否有不再使用的寄存器
        for (Map.Entry<String, IRVariable> entry : regToVar.entrySet()) {
            IRVariable var = entry.getValue();
            if (lastUse.get(var) < currentPos) {
                String reg = entry.getKey();
                // 更新映射
                varToReg.remove(var);
                regToVar.remove(reg);
                varToReg.put(variable, reg);
                regToVar.put(reg, variable);
                return reg;
            }
        }
        
        // 如果没有不再使用的寄存器，需要溢出到栈
        // 选择最晚使用的变量进行溢出
        String regToSpill = null;
        int maxLastUse = -1;
        for (Map.Entry<String, IRVariable> entry : regToVar.entrySet()) {
            IRVariable var = entry.getValue();
            if (lastUse.get(var) > maxLastUse) {
                maxLastUse = lastUse.get(var);
                regToSpill = entry.getKey();
            }
        }
        
        // 将变量保存到栈中
        IRVariable varToSpill = regToVar.get(regToSpill);
        if (!varToStack.containsKey(varToSpill)) {
            varToStack.put(varToSpill, stackOffset);
            stackOffset += 4;
        }
        asmList.add("    sw " + regToSpill + ", " + varToStack.get(varToSpill) + "(sp)");
        
        // 更新映射
        varToReg.remove(varToSpill);
        regToVar.remove(regToSpill);
        varToReg.put(variable, regToSpill);
        regToVar.put(regToSpill, variable);
        
        return regToSpill;
    }

    public void run() {
        asmList.add(".text");
        
        // 为栈分配空间
        if (stackOffset > 0) {
            asmList.add("    addi sp, sp, -" + stackOffset);
        }
        
        // 生成指令
        for (int i = 0; i < IRList.size(); i++) {
            Instruction inst = IRList.get(i);
            switch (inst.getKind()) {
                case MOV -> generateMov(inst, i);
                case ADD -> generateAdd(inst, i);
                case SUB -> generateSub(inst, i);
                case MUL -> generateMul(inst, i);
                case RET -> generateRet(inst, i);
            }
        }
    }

    private void generateMov(Instruction inst, int pos) {
        IRVariable result = (IRVariable) inst.getResult();
        String resultReg = allocateReg(result, pos);
        
        if (inst.getFrom() instanceof IRVariable) {
            IRVariable from = (IRVariable) inst.getFrom();
            String fromReg = allocateReg(from, pos);
            asmList.add("    mv " + resultReg + ", " + fromReg);
        } else {
            asmList.add("    li " + resultReg + ", " + inst.getFrom());
        }
    }

    private void generateBinaryOp(Instruction inst, String op, int pos) {
        IRVariable result = (IRVariable) inst.getResult();
        String resultReg = allocateReg(result, pos);
        
        String lhsReg;
        if (inst.getLHS() instanceof IRVariable) {
            lhsReg = allocateReg((IRVariable) inst.getLHS(), pos);
        } else {
            lhsReg = "t6";  // 临时使用t6
            asmList.add("    li " + lhsReg + ", " + inst.getLHS());
        }
        
        if (inst.getRHS() instanceof IRVariable) {
            String rhsReg = allocateReg((IRVariable) inst.getRHS(), pos);
            asmList.add("    " + op + " " + resultReg + ", " + lhsReg + ", " + rhsReg);
        } else {
            asmList.add("    " + op + "i " + resultReg + ", " + lhsReg + ", " + inst.getRHS());
        }
    }

    private void generateAdd(Instruction inst, int pos) {
        generateBinaryOp(inst, "add", pos);
    }

    private void generateSub(Instruction inst, int pos) {
        generateBinaryOp(inst, "sub", pos);
    }

    private void generateMul(Instruction inst, int pos) {
        generateBinaryOp(inst, "mul", pos);
    }

    private void generateRet(Instruction inst, int pos) {
        if (inst.getReturnValue() instanceof IRVariable) {
            IRVariable retVar = (IRVariable) inst.getReturnValue();
            String retReg = allocateReg(retVar, pos);
            asmList.add("    mv a0, " + retReg);
        } else {
            asmList.add("    li a0, " + inst.getReturnValue());
        }
        
        if (stackOffset > 0) {
            asmList.add("    addi sp, sp, " + stackOffset);
        }
    }

    public void dump(String path) {
        FileUtils.writeLines(path, asmList);
    }
}


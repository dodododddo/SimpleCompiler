package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Symbol;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    public SymbolTable table;
    private final Stack<Symbol> tokenStack = new Stack<>();
    private List<Instruction> IRList = new ArrayList<>();

    @Override
    public void whenAccept(Status currentStatus) {
        return;
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        String number = "^[0-9]+$";
        Symbol curSymbol = new Symbol(currentToken);
        if(currentToken.getText().matches(number)){
            curSymbol.value = IRImmediate.of(Integer.parseInt(currentToken.getText()));
        }else{
            curSymbol.value = IRVariable.named(currentToken.getText());
        }
        tokenStack.push(curSymbol);
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        Symbol lhs, rhs;
        Symbol curNonTerminal = new Symbol(production.head());
        IRVariable valueTemp;
        switch(production.index()) {
            case 6 -> {    //S -> id = E;
                rhs = tokenStack.pop();
                tokenStack.pop();
                lhs = tokenStack.pop();
                valueTemp = (IRVariable) lhs.value;
                curNonTerminal.value = null;
                IRList.add(Instruction.createMov(valueTemp, rhs.value));
                tokenStack.push(curNonTerminal);
            }
            case 7 -> {    //S -> return E;
                rhs = tokenStack.pop();
                tokenStack.pop();
                curNonTerminal.value = null;
                IRList.add(Instruction.createRet(rhs.value));
                tokenStack.push(curNonTerminal);
            }
            case 8 -> {    //E -> E + A;
                rhs = tokenStack.pop();
                tokenStack.pop();
                lhs = tokenStack.pop();
                valueTemp = IRVariable.temp();  //生成临时变量
                IRList.add(Instruction.createAdd(valueTemp, lhs.value, rhs.value));
                curNonTerminal.value = valueTemp;
                tokenStack.push(curNonTerminal);
            }
            case 9 -> {    //E -> E - A;
                rhs = tokenStack.pop();
                tokenStack.pop();
                lhs = tokenStack.pop();
                valueTemp = IRVariable.temp();  //生成临时变量
                IRList.add(Instruction.createSub(valueTemp, lhs.value, rhs.value));
                curNonTerminal.value = valueTemp;
                tokenStack.push(curNonTerminal);
            }
            case 10, 12, 14, 15 -> {    //E -> A; A -> B; B -> id; B -> IntConst;
                curNonTerminal.value = tokenStack.pop().value;
                tokenStack.push(curNonTerminal);
            }
            case 11 -> {    //A -> A * B;
                rhs = tokenStack.pop();
                tokenStack.pop();
                lhs = tokenStack.pop();
                valueTemp = IRVariable.temp();  //生成临时变量
                IRList.add(Instruction.createMul(valueTemp, lhs.value, rhs.value));
                curNonTerminal.value = valueTemp;
                tokenStack.push(curNonTerminal);
            }
            case 13 -> {    //B -> ( E );
                tokenStack.pop();
                rhs = tokenStack.pop();
                tokenStack.pop();
                curNonTerminal.value = rhs.value;
                tokenStack.push(curNonTerminal);
            }
        
            default -> {
                for(int i=0; i<production.body().size(); i++){
                    tokenStack.pop();
                }
                tokenStack.push(new Symbol(production.head()));
            }
        }
    }


  
    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
        this.table = table;
    }

    public List<Instruction> getIR() {
        // TODO
        return IRList;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}


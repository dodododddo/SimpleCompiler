package cn.edu.hitsz.compiler.parser;

import java.util.Objects;
import java.util.Stack;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Symbol;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

public class SemanticAnalyzer implements ActionObserver {

    private SymbolTable table;
    private final Stack<Symbol> tokenStack = new Stack<>();

    @Override
    public void whenAccept(Status currentStatus) {
        // 在接受时可以进行一些最终的检查或输出
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        Symbol curToken1, curToken2;
        Symbol curNonTerminal;
        switch(production.index()) {
            case 4 -> {    //S -> D id;
                curToken1 = tokenStack.pop();   //弹出id
                curToken2 = tokenStack.pop();   //弹出D
                // 将符号表中id的type更新为D的type
                this.table.get(curToken1.token.getText()).setType(curToken2.type);
                curNonTerminal = new Symbol(production.head());
                curNonTerminal.type = null;
                tokenStack.push(curNonTerminal);
            }
            case 5 -> {    //D -> int;
                curToken1 = tokenStack.pop();
                curNonTerminal = new Symbol(production.head());
                curNonTerminal.type = curToken1.type;
                tokenStack.push(curNonTerminal);
            }
            default -> {
                for(int i=0; i<production.body().size(); i++){
                    tokenStack.pop();
                }
                tokenStack.push(new Symbol(production.head()));
            }
        }
        // 这里可以根据具体的产生式进行不同的语义处理
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // 在移入时可以进行一些语义检查或记录
        Symbol curSymbol = new Symbol(currentToken);

        if(Objects.equals(currentToken.getKindId(), "int")){
            curSymbol.type = SourceCodeType.Int;
        }else{
            curSymbol.type = null;
        }

        tokenStack.push(curSymbol);
        // 这里可以根据具体的 token 进行不同的处理
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // 存储符号表以备后续使用
        this.table = table;
    }
}


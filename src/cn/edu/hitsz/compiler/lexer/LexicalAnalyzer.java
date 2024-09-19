package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private String sourceCode;
    private List<Token> tokens;
    private int position;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.tokens = new ArrayList<>();
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        this.sourceCode = FileUtils.readFile(path);
//        System.out.println(this.sourceCode);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        position = 0;
        while (position < sourceCode.length()) {
            char currentChar = sourceCode.charAt(position);
            
            if (Character.isWhitespace(currentChar)) {
                position++;
                continue;
            }

            if (Character.isLetter(currentChar) || currentChar == '_') {
                processIdentifierOrKeyword();
            } else if (Character.isDigit(currentChar)) {
                processNumber();
            } else {
                processSymbol();
            }
        }
        tokens.add(Token.eof());
    }

    private void processIdentifierOrKeyword() {
        StringBuilder identifier = new StringBuilder();
        while (position < sourceCode.length() && 
               (Character.isLetterOrDigit(sourceCode.charAt(position)) || sourceCode.charAt(position) == '_')) {
            identifier.append(sourceCode.charAt(position));
            position++;
        }
        String word = identifier.toString();
        if (TokenKind.isAllowed(word)){
            tokens.add(Token.simple(TokenKind.fromString(word)));
        }
        else{
            tokens.add(Token.normal(TokenKind.fromString("id"), word));
            symbolTable.add(word);
        }
    }

    private void processNumber() {
        StringBuilder number = new StringBuilder();
        while (position < sourceCode.length() && Character.isDigit(sourceCode.charAt(position))) {
            number.append(sourceCode.charAt(position));
            position++;
        }
        tokens.add(Token.normal(TokenKind.fromString("IntConst"), number.toString()));
    }

    private void processSymbol() {
        char currentChar = sourceCode.charAt(position);
        TokenKind kind = null;
        if (currentChar == ';'){
            tokens.add(Token.simple(TokenKind.fromString("Semicolon")));
        }
        else{
            tokens.add(Token.simple(TokenKind.fromString(String.valueOf(currentChar))));
        }
        position++;
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }
}

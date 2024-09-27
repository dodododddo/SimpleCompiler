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
        State state = State.START;
        StringBuilder currentToken = new StringBuilder();

        while (position < sourceCode.length()) {
            char currentChar = sourceCode.charAt(position);

            switch (state) {
                case START:
                    if (Character.isWhitespace(currentChar)) {
                        position++;
                    } else if (Character.isLetter(currentChar) || currentChar == '_') {
                        state = State.IDENTIFIER;
                        currentToken.append(currentChar);
                        position++;
                    } else if (Character.isDigit(currentChar)) {
                        state = State.NUMBER;
                        currentToken.append(currentChar);
                        position++;
                    } else if (currentChar == '*') {
                        state = State.STAR;
                        position++;
                    } else if (currentChar == '=') {
                        state = State.EQUAL;
                        position++;
                    } else {
                        state = State.SYMBOL;
                    }
                    break;

                case IDENTIFIER:
                    if (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
                        currentToken.append(currentChar);
                        position++;
                    } else {
                        addIdentifierOrKeyword(currentToken.toString());
                        state = State.START;
                        currentToken.setLength(0);
                    }
                    break;

                case NUMBER:
                    if (Character.isDigit(currentChar)) {
                        currentToken.append(currentChar);
                        position++;
                    } else {
                        addNumber(currentToken.toString());
                        state = State.START;
                        currentToken.setLength(0);
                    }
                    break;

                case SYMBOL:
                    addSymbol(currentChar);
                    position++;
                    state = State.START;
                    break;

                case STAR:
                    if (currentChar == '*') {
                        tokens.add(Token.simple(TokenKind.fromString("**")));
                        position++;
                    } else {
                        tokens.add(Token.simple(TokenKind.fromString("*")));
                    }
                    state = State.START;
                    break;

                case EQUAL:
                    if (currentChar == '=') {
                        tokens.add(Token.simple(TokenKind.fromString("==")));
                        position++;
                    } else {
                        tokens.add(Token.simple(TokenKind.fromString("=")));
                    }
                    state = State.START;
                    break;
            }
        }

        // 处理最后一个token
        if (state == State.IDENTIFIER) {
            addIdentifierOrKeyword(currentToken.toString());
        } else if (state == State.NUMBER) {
            addNumber(currentToken.toString());
        } else if (state == State.STAR || state == State.EQUAL){
            throw new RuntimeException("Illegal end token");
        }
        tokens.add(Token.eof());
    }

    private void addIdentifierOrKeyword(String word) {
        if (TokenKind.isAllowed(word)) {
            tokens.add(Token.simple(TokenKind.fromString(word)));
        } else {
            tokens.add(Token.normal(TokenKind.fromString("id"), word));
            symbolTable.add(word);
        }
    }

    private void addNumber(String number) {
        tokens.add(Token.normal(TokenKind.fromString("IntConst"), number));
    }

    private void addSymbol(char symbol) {
        if (symbol == ';') {
            tokens.add(Token.simple(TokenKind.fromString("Semicolon")));
        } else {
            tokens.add(Token.simple(TokenKind.fromString(String.valueOf(symbol))));
        }
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

    private enum State {
        START, IDENTIFIER, NUMBER, SYMBOL, STAR, EQUAL
    }
}

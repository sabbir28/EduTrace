package org.sabbir.edutrace.desktop.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * High-performance scientific math expression evaluator for EduTrace Desktop.
 * Supports basic arithmetic (+, -, *, /, %), scientific functions (sin, cos, tan,
 * asin, acos, atan, ln, log, sqrt, cbrt), powers (^), factorials (!), constants (pi, e),
 * nested parentheses, and Degree/Radian modes.
 */
public class ScientificMathEvaluator {

    public static class EvaluationResult {
        public final boolean success;
        public final double value;
        public final String formatted;
        public final String error;

        public EvaluationResult(double value, String formatted) {
            this.success = true;
            this.value = value;
            this.formatted = formatted;
            this.error = null;
        }

        public EvaluationResult(String error) {
            this.success = false;
            this.value = 0;
            this.formatted = null;
            this.error = error;
        }
    }

    public static EvaluationResult evaluate(String expression, boolean isDegreeMode) {
        if (expression == null || expression.trim().isEmpty()) {
            return new EvaluationResult("Empty expression");
        }
        try {
            String sanitized = sanitizeExpression(expression);
            Parser parser = new Parser(sanitized, isDegreeMode);
            double val = parser.parse();
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return new EvaluationResult("Error: Invalid calculation");
            }
            return new EvaluationResult(val, formatNumber(val));
        } catch (ArithmeticException e) {
            return new EvaluationResult(e.getMessage());
        } catch (Exception e) {
            return new EvaluationResult("Syntax Error");
        }
    }

    private static String sanitizeExpression(String expr) {
        return expr.replace("×", "*")
                .replace("÷", "/")
                .replace("π", "pi")
                .replace("√", "sqrt")
                .replaceAll("\\s+", "");
    }

    public static String formatNumber(double num) {
        if (Double.isNaN(num)) return "Error";
        if (Double.isInfinite(num)) return num > 0 ? "Infinity" : "-Infinity";

        // Exact integer check
        if (Math.abs(num - Math.round(num)) < 1e-11) {
            return String.valueOf(Math.round(num));
        }

        // Format floating point numbers nicely up to 10 decimal places, no trailing zeros
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("#.##########", symbols);
        return df.format(num);
    }

    /**
     * Recursive Descent Math Parser
     * Grammar:
     * expression = term | expression `+` term | expression `-` term
     * term       = factor | term `*` factor | term `/` factor | term `%` factor
     * factor     = `+` factor | `-` factor | `(` expression `)` | number | function factor | factor `^` factor | factor `!`
     */
    private static class Parser {
        private final String str;
        private final boolean isDegreeMode;
        private int pos = -1;
        private int ch;

        Parser(String str, boolean isDegreeMode) {
            this.str = str;
            this.isDegreeMode = isDegreeMode;
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected char: " + (char) ch);
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0) throw new ArithmeticException("Division by zero");
                    x /= divisor;
                } else if (eat('%')) {
                    double modulus = parseFactor();
                    if (modulus == 0) throw new ArithmeticException("Division by zero");
                    x %= modulus;
                } else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return +parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) nextChar();
                String func = str.substring(startPos, this.pos).toLowerCase();

                if (func.equals("pi")) {
                    x = Math.PI;
                } else if (func.equals("e")) {
                    x = Math.E;
                } else {
                    double arg = parseFactor();
                    switch (func) {
                        case "sqrt":
                            if (arg < 0) throw new ArithmeticException("Negative square root");
                            x = Math.sqrt(arg);
                            break;
                        case "cbrt":
                            x = Math.cbrt(arg);
                            break;
                        case "sin":
                            x = Math.sin(isDegreeMode ? Math.toRadians(arg) : arg);
                            break;
                        case "cos":
                            x = Math.cos(isDegreeMode ? Math.toRadians(arg) : arg);
                            break;
                        case "tan":
                            if (isDegreeMode && Math.abs(arg % 180) == 90) {
                                throw new ArithmeticException("Undefined tan(90°)");
                            }
                            x = Math.tan(isDegreeMode ? Math.toRadians(arg) : arg);
                            break;
                        case "asin":
                            if (arg < -1 || arg > 1) throw new ArithmeticException("asin out of range [-1, 1]");
                            x = Math.asin(arg);
                            if (isDegreeMode) x = Math.toDegrees(x);
                            break;
                        case "acos":
                            if (arg < -1 || arg > 1) throw new ArithmeticException("acos out of range [-1, 1]");
                            x = Math.acos(arg);
                            if (isDegreeMode) x = Math.toDegrees(x);
                            break;
                        case "atan":
                            x = Math.atan(arg);
                            if (isDegreeMode) x = Math.toDegrees(x);
                            break;
                        case "log":
                            if (arg <= 0) throw new ArithmeticException("Non-positive logarithm");
                            x = Math.log10(arg);
                            break;
                        case "ln":
                            if (arg <= 0) throw new ArithmeticException("Non-positive natural logarithm");
                            x = Math.log(arg);
                            break;
                        case "abs":
                            x = Math.abs(arg);
                            break;
                        default:
                            throw new RuntimeException("Unknown function: " + func);
                    }
                }
            } else {
                throw new RuntimeException("Unexpected char: " + (char) ch);
            }

            // Power (x ^ y) and Factorial (x!)
            for (;;) {
                if (eat('^')) {
                    double exponent = parseFactor();
                    x = Math.pow(x, exponent);
                } else if (eat('!')) {
                    x = factorial(x);
                } else {
                    break;
                }
            }

            return x;
        }

        private double factorial(double n) {
            if (n < 0 || n != Math.floor(n)) {
                throw new ArithmeticException("Factorial requires non-negative integer");
            }
            if (n > 170) throw new ArithmeticException("Factorial overflow");
            double res = 1;
            for (int i = 2; i <= (int) n; i++) {
                res *= i;
            }
            return res;
        }
    }
}


package org.sabbir.edutrace.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * High-performance scientific math expression evaluator for EduTrace.
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

        if (Math.abs(num) >= 1e11 || (Math.abs(num) < 1e-6 && num != 0)) {
            DecimalFormat df = new DecimalFormat("0.######E0", DecimalFormatSymbols.getInstance(Locale.US));
            return df.format(num);
        } else {
            DecimalFormat df = new DecimalFormat("#,##0.########", DecimalFormatSymbols.getInstance(Locale.US));
            return df.format(num);
        }
    }

    private static class Parser {
        private final String str;
        private final boolean isDegreeMode;
        private int pos = -1;
        private int ch;

        Parser(String str, boolean isDegreeMode) {
            this.str = str;
            this.isDegreeMode = isDegreeMode;
            nextChar();
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
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
            return x;
        }

        // Expression = Term (+ or - Term)*
        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        // Term = Factor (*, /, % Factor)*
        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) {
                    x *= parseFactor();
                } else if (eat('/')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-15) {
                        throw new ArithmeticException("Cannot divide by 0");
                    }
                    x /= divisor;
                } else if (eat('%')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-15) {
                        throw new ArithmeticException("Cannot divide by 0");
                    }
                    x %= divisor;
                } else {
                    return x;
                }
            }
        }

        // Factor = Power (^ Power)*
        private double parseFactor() {
            double x = parseUnary();
            if (eat('^')) {
                double exponent = parseFactor(); // Right associative
                x = Math.pow(x, exponent);
            }
            return x;
        }

        // Unary = + Unary | - Unary | Primary (!)
        private double parseUnary() {
            if (eat('+')) return parseUnary();
            if (eat('-')) return -parseUnary();

            double x = parsePrimary();
            if (eat('!')) {
                x = factorial(x);
            }
            return x;
        }

        // Primary = number | constant | function | ( expression )
        private double parsePrimary() {
            double x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z') {
                while (ch >= 'a' && ch <= 'z') nextChar();
                String func = str.substring(startPos, this.pos);

                if ("pi".equals(func)) {
                    x = Math.PI;
                } else if ("e".equals(func)) {
                    x = Math.E;
                } else {
                    // Function calls expect an argument in parentheses or factor
                    double arg;
                    if (eat('(')) {
                        arg = parseExpression();
                        if (!eat(')')) throw new RuntimeException("Missing ')'");
                    } else {
                        arg = parseFactor();
                    }

                    switch (func) {
                        case "sqrt":
                            if (arg < 0) throw new ArithmeticException("Invalid sqrt domain");
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
                            x = Math.tan(isDegreeMode ? Math.toRadians(arg) : arg);
                            break;
                        case "asin":
                            if (arg < -1 || arg > 1) throw new ArithmeticException("Invalid asin domain");
                            double asin = Math.asin(arg);
                            x = isDegreeMode ? Math.toDegrees(asin) : asin;
                            break;
                        case "acos":
                            if (arg < -1 || arg > 1) throw new ArithmeticException("Invalid acos domain");
                            double acos = Math.acos(arg);
                            x = isDegreeMode ? Math.toDegrees(acos) : acos;
                            break;
                        case "atan":
                            double atan = Math.atan(arg);
                            x = isDegreeMode ? Math.toDegrees(atan) : atan;
                            break;
                        case "ln":
                            if (arg <= 0) throw new ArithmeticException("Invalid ln domain");
                            x = Math.log(arg);
                            break;
                        case "log":
                            if (arg <= 0) throw new ArithmeticException("Invalid log domain");
                            x = Math.log10(arg);
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

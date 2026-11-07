package org.sabbir.edutrace.desktop.ui.tabs;

import org.sabbir.edutrace.desktop.utils.ScientificMathEvaluator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CalculatorTab extends JPanel {

    private final StringBuilder exprBuilder = new StringBuilder();
    private boolean isDegreeMode = true;

    private JLabel expressionLabel;
    private JLabel resultLabel;
    private JButton btnDegRad;
    private DefaultListModel<String> historyModel;
    private JList<String> historyList;

    public CalculatorTab() {
        setLayout(new BorderLayout(16, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        initUI();
    }

    private void initUI() {
        // Main split: Left = Calculator Keypad & Display, Right = History Panel
        JPanel leftPanel = new JPanel(new BorderLayout(12, 12));
        leftPanel.setOpaque(false);

        // Display Card
        JPanel displayCard = createDisplayCard();
        leftPanel.add(displayCard, BorderLayout.NORTH);

        // Keypad Card
        JPanel keypadCard = createKeypadCard();
        leftPanel.add(keypadCard, BorderLayout.CENTER);

        // History Card (Right side)
        JPanel historyCard = createHistoryCard();
        historyCard.setPreferredSize(new Dimension(280, 0));

        add(leftPanel, BorderLayout.CENTER);
        add(historyCard, BorderLayout.EAST);
    }

    private JPanel createDisplayCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        expressionLabel = new JLabel("0");
        expressionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        expressionLabel.setForeground(new Color(0x94, 0xA3, 0xB8));
        expressionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        resultLabel = new JLabel("= 0");
        resultLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        resultLabel.setForeground(new Color(0xE2, 0xB7, 0x14));
        resultLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        card.add(expressionLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(resultLabel);
        return card;
    }

    private JPanel createKeypadCard() {
        JPanel card = createStyledCard();
        card.setLayout(new GridLayout(6, 6, 8, 8));

        String[][] keys = {
                {"DEG", "C", "AC", "⌫", "(", ")"},
                {"sin", "cos", "tan", "^", "sqrt", "÷"},
                {"asin", "acos", "atan", "!", "cbrt", "×"},
                {"ln", "log", "7", "8", "9", "-"},
                {"pi", "e", "4", "5", "6", "+"},
                {"%", "abs", "1", "2", "3", "="}
        };

        for (String[] row : keys) {
            for (String key : row) {
                JButton btn = new JButton(key);
                btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
                btn.setFocusPainted(false);

                if (key.equals("=")) {
                    btn.setBackground(new Color(0xE2, 0xB7, 0x14));
                    btn.setForeground(new Color(0x0F, 0x17, 0x2A));
                } else if (key.equals("AC") || key.equals("C")) {
                    btn.setBackground(new Color(0xEF, 0x44, 0x44));
                    btn.setForeground(Color.WHITE);
                } else if (key.equals("DEG")) {
                    btnDegRad = btn;
                    btn.setBackground(new Color(0x33, 0x41, 0x55));
                    btn.setForeground(new Color(0xE2, 0xB7, 0x14));
                } else if (key.matches("[0-9]")) {
                    btn.setBackground(new Color(0x1E, 0x29, 0x3B));
                    btn.setForeground(Color.WHITE);
                } else {
                    btn.setBackground(new Color(0x33, 0x41, 0x55));
                    btn.setForeground(new Color(0x38, 0xBD, 0xF8));
                }

                btn.addActionListener(e -> handleKeyPress(key));
                card.add(btn);
            }
        }

        // Additional bottom row for 0, ., and quick shortcuts
        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setOpaque(false);
        wrapper.add(card, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new GridLayout(1, 3, 8, 8));
        bottomRow.setOpaque(false);

        JButton btnZero = new JButton("0");
        btnZero.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnZero.addActionListener(e -> handleKeyPress("0"));

        JButton btnDot = new JButton(".");
        btnDot.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnDot.addActionListener(e -> handleKeyPress("."));

        JButton btnEval = new JButton("Evaluate =");
        btnEval.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnEval.setBackground(new Color(0x10, 0xB9, 0x81));
        btnEval.setForeground(Color.WHITE);
        btnEval.addActionListener(e -> handleKeyPress("="));

        bottomRow.add(btnZero);
        bottomRow.add(btnDot);
        bottomRow.add(btnEval);

        wrapper.add(bottomRow, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel createHistoryCard() {
        JPanel card = createStyledCard();
        card.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("CALCULATION HISTORY");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(0xE2, 0xB7, 0x14));

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = historyList.getSelectedValue();
                if (selected != null && selected.contains("=")) {
                    String[] parts = selected.split("=");
                    setExpression(parts[0].trim());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setBorder(null);

        JButton btnClearHistory = new JButton("Clear History");
        btnClearHistory.addActionListener(e -> historyModel.clear());

        card.add(title, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(btnClearHistory, BorderLayout.SOUTH);
        return card;
    }

    private void handleKeyPress(String key) {
        switch (key) {
            case "DEG":
                isDegreeMode = !isDegreeMode;
                btnDegRad.setText(isDegreeMode ? "DEG" : "RAD");
                evaluateExpression(false);
                break;
            case "C":
                if (exprBuilder.length() > 0) {
                    exprBuilder.deleteCharAt(exprBuilder.length() - 1);
                }
                updateDisplay();
                break;
            case "AC":
                exprBuilder.setLength(0);
                updateDisplay();
                break;
            case "⌫":
                if (exprBuilder.length() > 0) {
                    exprBuilder.deleteCharAt(exprBuilder.length() - 1);
                }
                updateDisplay();
                break;
            case "=":
                evaluateExpression(true);
                break;
            case "sin": case "cos": case "tan":
            case "asin": case "acos": case "atan":
            case "ln": case "log": case "sqrt": case "cbrt": case "abs":
                exprBuilder.append(key).append("(");
                updateDisplay();
                break;
            case "pi":
                exprBuilder.append("pi");
                updateDisplay();
                break;
            case "e":
                exprBuilder.append("e");
                updateDisplay();
                break;
            case "÷":
                exprBuilder.append("/");
                updateDisplay();
                break;
            case "×":
                exprBuilder.append("*");
                updateDisplay();
                break;
            default:
                exprBuilder.append(key);
                updateDisplay();
                break;
        }
    }

    private void updateDisplay() {
        String current = exprBuilder.length() > 0 ? exprBuilder.toString() : "0";
        expressionLabel.setText(current);
        evaluateExpression(false);
    }

    private void evaluateExpression(boolean addToHistory) {
        String expr = exprBuilder.toString().trim();
        if (expr.isEmpty()) {
            resultLabel.setText("= 0");
            return;
        }

        ScientificMathEvaluator.EvaluationResult res = ScientificMathEvaluator.evaluate(expr, isDegreeMode);
        if (res.success) {
            resultLabel.setText("= " + res.formatted);
            resultLabel.setForeground(new Color(0xE2, 0xB7, 0x14));
            if (addToHistory) {
                historyModel.addElement(expr + " = " + res.formatted);
                // Also set expression to formatted answer for chaining
                exprBuilder.setLength(0);
                exprBuilder.append(res.formatted);
                expressionLabel.setText(exprBuilder.toString());
            }
        } else {
            resultLabel.setText("= " + (addToHistory ? res.error : "..."));
            resultLabel.setForeground(new Color(0x94, 0xA3, 0xB8));
        }
    }

    public void setExpression(String expression) {
        exprBuilder.setLength(0);
        if (expression != null && !expression.trim().isEmpty()) {
            exprBuilder.append(expression.trim());
        }
        updateDisplay();
    }

    private JPanel createStyledCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1E, 0x29, 0x3B));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(0x33, 0x41, 0x55));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        return card;
    }
}


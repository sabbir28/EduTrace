package org.sabbir.edutrace.desktop;

import org.junit.Test;
import org.sabbir.edutrace.desktop.data.DesktopQuoteManager;
import org.sabbir.edutrace.desktop.data.DesktopTask;
import org.sabbir.edutrace.desktop.utils.ScientificMathEvaluator;

import static org.junit.Assert.*;

public class DesktopAppTest {

    @Test
    public void testMathEvaluation() {
        ScientificMathEvaluator.EvaluationResult res = ScientificMathEvaluator.evaluate("25 * 4", true);
        assertTrue(res.success);
        assertEquals("100", res.formatted);

        ScientificMathEvaluator.EvaluationResult res2 = ScientificMathEvaluator.evaluate("sqrt(144) + 10^2", true);
        assertTrue(res2.success);
        assertEquals("112", res2.formatted);

        ScientificMathEvaluator.EvaluationResult resSin = ScientificMathEvaluator.evaluate("sin(90)", true);
        assertTrue(resSin.success);
        assertEquals("1", resSin.formatted);
    }

    @Test
    public void testQuoteManager() {
        DesktopQuoteManager manager = new DesktopQuoteManager();
        assertNotNull(manager.getCurrentQuote());
        assertFalse(manager.getCurrentQuote().isEmpty());

        manager.setBengali(true);
        assertNotNull(manager.getCurrentQuote());
        assertFalse(manager.getCurrentQuote().isEmpty());
    }

    @Test
    public void testDesktopTask() {
        DesktopTask task = new DesktopTask("1", "Study Math", false);
        assertEquals("1", task.getId());
        assertEquals("Study Math", task.getTitle());
        assertFalse(task.isCompleted());

        task.setCompleted(true);
        assertTrue(task.isCompleted());
    }
}


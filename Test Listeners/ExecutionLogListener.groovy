import com.kms.katalon.core.annotation.BeforeTestCase
import com.kms.katalon.core.annotation.BeforeTestSuite
import com.kms.katalon.core.annotation.AfterTestCase
import com.kms.katalon.core.annotation.AfterTestSuite
import com.kms.katalon.core.context.TestCaseContext
import com.kms.katalon.core.context.TestSuiteContext
import internal.GlobalVariable

import java.text.SimpleDateFormat

class ExecutionLogListener {

    private File logFile = null
    private String suiteName = ''
    private long suiteStartMillis = 0L
    private Map<String, Long> testcaseStart = [:]

    @BeforeTestSuite
    def beforeTestSuite(TestSuiteContext testSuiteContext) {
        suiteName = resolveSuiteName(testSuiteContext)
        ensureLogFile(useSuiteName: true, suite: suiteName, testcase: null)
        suiteStartMillis = System.currentTimeMillis()

        appendLine('=== TEST SUITE START ===')
        appendLine("timestamp=${now()}")
        appendLine("suiteName=${suiteName}")
        appendLine('')
    }

    @BeforeTestCase
    def beforeTestCase(TestCaseContext testCaseContext) {
        String tcName = resolveTestCaseName(testCaseContext)
        String tcId = safeInvoke(testCaseContext, 'getTestCaseId') ?: ''
        String key = "${tcName}|${tcId}"

        // Jika belum ada file (mis. run single Test Case), buat file berdasarkan testcase
        if (logFile == null) {
            ensureLogFile(useSuiteName: false, suite: suiteName, testcase: tcName)
        }

        testcaseStart[key] = System.currentTimeMillis()

        appendLine(">>> TEST CASE START")
        appendLine("timestamp=${now()}")
        appendLine("testcaseName=${tcName}")
        appendLine("testcaseId=${tcId}")
        appendLine('')
    }

    @AfterTestCase
    def afterTestCase(TestCaseContext testCaseContext) {
        String tcName = resolveTestCaseName(testCaseContext)
        String tcId = safeInvoke(testCaseContext, 'getTestCaseId') ?: ''
        String key = "${tcName}|${tcId}"

        Long start = testcaseStart.remove(key)
        long durationMs = start != null ? (System.currentTimeMillis() - start) : -1

        String status = (testCaseContext?.getTestCaseStatus() != null) ? testCaseContext.getTestCaseStatus().toString() : 'UNKNOWN'

        appendLine(">>> TEST CASE END")
        appendLine("timestamp=${now()}")
        appendLine("testcaseName=${tcName}")
        appendLine("testcaseId=${tcId}")
        appendLine("status=${status}")
        appendLine("durationMs=${durationMs}")

        // Catat semua Test Case variables (final values)
        appendLine("variables:")
        try {
            def vars = testCaseContext.getTestCaseVariables()
            if (vars == null || vars.isEmpty()) {
                appendLine("  - (none)")
            } else {
                vars.each { k, v ->
                    appendLine("  - ${k}=${formatValue(v)}")
                }
            }
        } catch (ignored) {
            appendLine("  - (error reading variables)")
        }

        appendLine('')
    }

    @AfterTestSuite
    def afterTestSuite(TestSuiteContext testSuiteContext) {
        long totalDuration = (suiteStartMillis > 0) ? (System.currentTimeMillis() - suiteStartMillis) : -1
        appendLine('=== TEST SUITE END ===')
        appendLine("timestamp=${now()}")
        appendLine("suiteName=${suiteName}")
        appendLine("totalDurationMs=${totalDuration}")
        appendLine('')
    }

    // ---------------- helpers ----------------
    private void ensureLogFile(Map args) {
        boolean useSuiteName = args.useSuiteName as boolean
        String suite = args.suite as String
        String testcase = args.testcase as String

        String baseDir = (GlobalVariable.SCREENSHOT_PATH?.toString()?.trim() ?: 'Screenshot/')
        if (!baseDir.endsWith(File.separator)) baseDir += File.separator
        new File(baseDir).mkdirs()

        String baseName = ''
        if (useSuiteName && suite) {
            baseName = sanitize(suite)
        } else if (!useSuiteName && testcase) {
            baseName = sanitize(testcase)
        }

        if (!baseName) baseName = 'execution'

        String stamp = new SimpleDateFormat('yyyyMMdd_HHmmss').format(new Date())
        String fileName = "execution_log_${baseName}_${stamp}.txt"
        logFile = new File(baseDir, fileName)
        if (!logFile.exists()) logFile.createNewFile()
    }

    private void appendLine(String line) {
        if (logFile == null) return
        logFile.append(line + System.lineSeparator())
    }

    private String sanitize(String s) {
        if (!s) return ''
        String cleaned = s.replaceAll(/[\\/:*?"<>|]/, '_')
        cleaned = cleaned.replaceAll(/\\s+/, '_').replaceAll(/_+/, '_').replaceAll(/^_+|_+$/, '')
        return (cleaned.length() > 80) ? cleaned.substring(0, 80) : cleaned
    }

    private String formatValue(Object v) {
        if (v == null) return 'null'
        if (v instanceof Map) {
            return v.collect { k, val -> "${k}=${formatValue(val)}" }.join(', ')
        }
        if (v instanceof List) {
            return v.collect { formatValue(it) }.join(', ')
        }
        return v.toString()
    }

    private String now() {
        return new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.SSS').format(new Date())
    }

    private String resolveSuiteName(TestSuiteContext ctx) {
        if (ctx == null) return ''
        // coba getTestSuiteName() jika ada, jika tidak pakai getTestSuiteId()
        try {
            return ctx.getTestSuiteName()
        } catch (MissingMethodException ignored) { }
        try {
            String id = ctx.getTestSuiteId()
            if (id) {
                def parts = id.tokenize('/')
                return parts ? parts.last() : id
            }
        } catch (Exception ignored) { }
        return ''
    }

    private String resolveTestCaseName(TestCaseContext ctx) {
        if (ctx == null) return ''
        try {
            return ctx.getTestCaseName()
        } catch (MissingMethodException ignored) { }
        try {
            String id = safeInvoke(ctx, 'getTestCaseId')
            if (id) {
                def parts = id.tokenize('/')
                return parts ? parts.last() : id
            }
        } catch (Exception ignored) { }
        return ''
    }

    private String safeInvoke(Object target, String methodName) {
        if (target == null) return null
        try {
            def m = target.getClass().getMethod(methodName)
            def val = m.invoke(target)
            return val != null ? val.toString() : null
        } catch (Exception ignored) {
            return null
        }
    }
}
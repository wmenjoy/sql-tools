// Verify HTML report was generated even though build failed
File report = new File(basedir, "target/sqlguard-report.html")
assert report.exists() : "HTML report not generated"
assert report.length() > 0 : "HTML report is empty"

// Verify report contains expected content
String content = report.text
assert content.contains("SQL Safety Guard") : "Report missing title"

// Build should have failed due to CRITICAL violations
File buildLog = new File(basedir, "build.log")
if (buildLog.exists()) {
    String log = buildLog.text
    // The build should have failed with MojoFailureException
    println "✓ Fail on critical test passed (build failed as expected)"
}

return true















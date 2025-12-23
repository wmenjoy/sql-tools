// Verify HTML report was generated
File report = new File(basedir, "target/sqlguard-report.html")
assert report.exists() : "HTML report not generated"
assert report.length() > 0 : "HTML report is empty"

// Verify report contains expected content
String content = report.text
assert content.contains("SQL Safety Guard") : "Report missing title"

// Build should pass despite violations (failOnCritical=false)
println "✓ With violations test passed (build did not fail)"
return true















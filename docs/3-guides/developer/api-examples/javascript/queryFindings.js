/**
 * Example: Query audit findings using fetch API (Node.js or Browser).
 *
 * This script demonstrates how to query the SQL Audit Service API
 * using the native fetch API.
 *
 * Prerequisites:
 *   - Audit service running at localhost:8090
 *   - Node.js 18+ (for native fetch support)
 *
 * Usage:
 *   node queryFindings.js
 */

const BASE_URL = 'http://localhost:8090/api/v1';

/**
 * Query recent CRITICAL audit findings.
 */
async function queryCriticalFindings(limit = 10) {
    const url = new URL(`${BASE_URL}/audits`);
    url.searchParams.append('riskLevel', 'CRITICAL');
    url.searchParams.append('size', limit.toString());
    url.searchParams.append('sort', 'createdAt,desc');

    try {
        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        console.log(`Total CRITICAL findings: ${data.totalElements}`);
        console.log(`Page ${data.number + 1} of ${data.totalPages}`);
        console.log('\nFindings:');

        data.content.forEach(finding => {
            console.log('----------------------------------------');
            console.log(`ID: ${finding.id}`);
            console.log(`SQL: ${finding.sql}`);
            console.log(`Risk Score: ${finding.riskScore}`);
            console.log(`Checker: ${finding.checkerId}`);
            console.log(`Message: ${finding.message}`);
            console.log(`Recommendation: ${finding.recommendation}`);
        });

        return data;
    } catch (error) {
        console.error('Error querying audit findings:', error.message);
        throw error;
    }
}

/**
 * Query audit findings with custom filters.
 */
async function queryWithFilters(options = {}) {
    const {
        sqlId,
        riskLevel,
        startTime,
        endTime,
        page = 0,
        size = 20
    } = options;

    const url = new URL(`${BASE_URL}/audits`);
    url.searchParams.append('page', page.toString());
    url.searchParams.append('size', size.toString());

    if (sqlId) url.searchParams.append('sqlId', sqlId);
    if (riskLevel) url.searchParams.append('riskLevel', riskLevel);
    if (startTime) url.searchParams.append('startTime', startTime);
    if (endTime) url.searchParams.append('endTime', endTime);

    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log(`Filtered results: ${data.totalElements} findings`);

    return data;
}

/**
 * Get a specific audit report by ID.
 */
async function getAuditById(reportId) {
    const url = `${BASE_URL}/audits/${reportId}`;

    try {
        const response = await fetch(url);

        if (!response.ok) {
            if (response.status === 404) {
                console.log(`Audit report not found: ${reportId}`);
                return null;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const report = await response.json();

        console.log(`Audit Report: ${report.id}`);
        console.log(`SQL: ${report.sql}`);
        console.log(`Risk Score: ${report.riskScore}`);
        console.log(`Metadata:`, report.metadata);

        return report;
    } catch (error) {
        console.error('Error retrieving audit report:', error.message);
        return null;
    }
}

/**
 * Get dashboard statistics.
 */
async function getDashboardStats() {
    const url = `${BASE_URL}/statistics/dashboard`;

    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }

    const stats = await response.json();

    console.log('Dashboard Statistics:');
    console.log(`Total Findings: ${stats.totalFindings}`);
    console.log(`Critical: ${stats.criticalCount}`);
    console.log(`High: ${stats.highCount}`);
    console.log(`Medium: ${stats.mediumCount}`);
    console.log(`Low: ${stats.lowCount}`);

    if (stats.topRiskySql) {
        console.log('\nTop Risky SQL Patterns:');
        stats.topRiskySql.slice(0, 5).forEach(sql => {
            console.log(`  - Score ${sql.riskScore}: ${sql.sql.substring(0, 50)}...`);
        });
    }

    return stats;
}

/**
 * Get trend statistics over time.
 */
async function getTrendStats(startTime, endTime, granularity = 'DAY') {
    const url = new URL(`${BASE_URL}/statistics/trends`);
    url.searchParams.append('startTime', startTime);
    url.searchParams.append('endTime', endTime);
    url.searchParams.append('granularity', granularity);

    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }

    const trends = await response.json();

    console.log(`Trend data (${granularity}):`);
    trends.forEach(point => {
        console.log(`  ${point.date}: ${point.count} findings ` +
                    `(${point.criticalCount || 0} critical)`);
    });

    return trends;
}

/**
 * Main function demonstrating various API calls.
 */
async function main() {
    try {
        console.log('=== Querying Critical Findings ===');
        await queryCriticalFindings(5);

        console.log('\n=== Dashboard Statistics ===');
        await getDashboardStats();

        console.log('\n=== Trend Statistics (Last 7 Days) ===');
        const endTime = new Date().toISOString();
        const startTime = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
        await getTrendStats(startTime, endTime, 'DAY');

        console.log('\n=== Filtered Query ===');
        await queryWithFilters({
            riskLevel: 'HIGH',
            startTime: startTime,
            size: 10
        });
    } catch (error) {
        console.error('Error in main:', error.message);
        process.exit(1);
    }
}

// Run main function if executed directly
if (require.main === module) {
    main();
}

// Export functions for use as a module
module.exports = {
    queryCriticalFindings,
    queryWithFilters,
    getAuditById,
    getDashboardStats,
    getTrendStats
};

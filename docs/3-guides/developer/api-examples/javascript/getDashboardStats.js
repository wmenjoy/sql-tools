/**
 * Example: Get dashboard statistics using axios (Node.js).
 *
 * This script demonstrates how to query dashboard statistics
 * using the axios HTTP client library.
 *
 * Prerequisites:
 *   - Audit service running at localhost:8090
 *   - axios library: npm install axios
 *
 * Usage:
 *   node getDashboardStats.js
 */

const axios = require('axios');

const BASE_URL = 'http://localhost:8090/api/v1';

/**
 * Get dashboard statistics using axios.
 */
async function getDashboardStats() {
    try {
        const response = await axios.get(`${BASE_URL}/statistics/dashboard`);
        const stats = response.data;

        console.log('Dashboard Statistics:');
        console.log(`Total Findings: ${stats.totalFindings}`);
        console.log(`Critical: ${stats.criticalCount}`);
        console.log(`High: ${stats.highCount}`);
        console.log(`Medium: ${stats.mediumCount}`);
        console.log(`Low: ${stats.lowCount}`);

        if (stats.topRiskySql && stats.topRiskySql.length > 0) {
            console.log('\nTop Risky SQL Patterns:');
            stats.topRiskySql.slice(0, 5).forEach((sql, index) => {
                console.log(`${index + 1}. Score ${sql.riskScore}: ${sql.sql.substring(0, 60)}...`);
                console.log(`   Occurrences: ${sql.occurrences}`);
            });
        }

        if (stats.trendData && stats.trendData.length > 0) {
            console.log('\nRecent Trends:');
            stats.trendData.forEach(point => {
                console.log(`  ${point.date}: ${point.count} findings`);
            });
        }

        return stats;
    } catch (error) {
        if (error.response) {
            // Server responded with error status
            console.error(`Error ${error.response.status}: ${error.response.data.message}`);
        } else if (error.request) {
            // No response received
            console.error('No response from server. Is the audit service running?');
        } else {
            // Error setting up request
            console.error('Error:', error.message);
        }
        throw error;
    }
}

/**
 * Query audit findings with axios and error handling.
 */
async function queryAuditsWithAxios(params = {}) {
    try {
        const response = await axios.get(`${BASE_URL}/audits`, {
            params: {
                riskLevel: params.riskLevel || 'CRITICAL',
                size: params.size || 10,
                page: params.page || 0,
                sort: params.sort || 'createdAt,desc'
            },
            timeout: 5000, // 5 second timeout
            headers: {
                'Accept': 'application/json'
            }
        });

        const data = response.data;
        console.log(`Found ${data.totalElements} findings`);

        return data;
    } catch (error) {
        handleAxiosError(error);
        throw error;
    }
}

/**
 * Update checker configuration using axios PUT request.
 */
async function updateCheckerConfig(checkerId, config) {
    try {
        const response = await axios.put(
            `${BASE_URL}/configuration/checkers/${checkerId}`,
            config,
            {
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );

        console.log(`Checker ${checkerId} configuration updated:`);
        console.log(JSON.stringify(response.data, null, 2));

        return response.data;
    } catch (error) {
        handleAxiosError(error);
        throw error;
    }
}

/**
 * Get checker information with retry logic.
 */
async function getCheckerInfo(checkerId, retries = 3) {
    for (let i = 0; i < retries; i++) {
        try {
            const response = await axios.get(
                `${BASE_URL}/configuration/checkers/${checkerId}`,
                { timeout: 3000 }
            );

            return response.data;
        } catch (error) {
            if (i === retries - 1) {
                console.error(`Failed after ${retries} attempts`);
                throw error;
            }
            console.log(`Retry ${i + 1}/${retries}...`);
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }
}

/**
 * Handle axios errors with detailed logging.
 */
function handleAxiosError(error) {
    if (error.response) {
        // Server responded with error status
        console.error('API Error:', {
            status: error.response.status,
            statusText: error.response.statusText,
            message: error.response.data.message || error.response.data,
            timestamp: error.response.data.timestamp
        });

        if (error.response.data.details) {
            console.error('Details:', error.response.data.details);
        }
    } else if (error.request) {
        // No response received
        console.error('Network Error: No response received from server');
        console.error('Is the audit service running at', BASE_URL, '?');
    } else {
        // Error setting up request
        console.error('Request Error:', error.message);
    }
}

/**
 * Example with axios interceptors for logging and auth.
 */
function createAxiosInstanceWithInterceptors() {
    const instance = axios.create({
        baseURL: BASE_URL,
        timeout: 10000,
        headers: {
            'Content-Type': 'application/json'
        }
    });

    // Request interceptor
    instance.interceptors.request.use(
        config => {
            console.log(`[${new Date().toISOString()}] ${config.method.toUpperCase()} ${config.url}`);
            // Add auth token if available
            const token = process.env.API_TOKEN;
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
            return config;
        },
        error => {
            console.error('Request Error:', error);
            return Promise.reject(error);
        }
    );

    // Response interceptor
    instance.interceptors.response.use(
        response => {
            console.log(`[${new Date().toISOString()}] Response ${response.status}`);
            return response;
        },
        error => {
            handleAxiosError(error);
            return Promise.reject(error);
        }
    );

    return instance;
}

/**
 * Main function demonstrating axios usage.
 */
async function main() {
    try {
        console.log('=== Getting Dashboard Statistics ===');
        await getDashboardStats();

        console.log('\n=== Querying Audits with Axios ===');
        await queryAuditsWithAxios({ riskLevel: 'HIGH', size: 5 });

        console.log('\n=== Getting Checker Info with Retry ===');
        const checkerInfo = await getCheckerInfo('TABLE_LOCK');
        console.log('Checker configuration:', checkerInfo);

        console.log('\n=== Using Axios Instance with Interceptors ===');
        const api = createAxiosInstanceWithInterceptors();
        const stats = await api.get('/statistics/dashboard');
        console.log('Total findings:', stats.data.totalFindings);

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
    getDashboardStats,
    queryAuditsWithAxios,
    updateCheckerConfig,
    getCheckerInfo,
    createAxiosInstanceWithInterceptors
};

"""
Example: Query audit findings using Python requests library.

This script demonstrates how to query the SQL Audit Service API
to retrieve critical findings using the Python requests library.

Prerequisites:
  - Audit service running at localhost:8090
  - requests library: pip install requests

Usage:
  python query_findings.py
"""

import requests
from datetime import datetime, timedelta
from typing import List, Dict, Optional


BASE_URL = "http://localhost:8090/api/v1"


def query_critical_findings(limit: int = 10) -> Dict:
    """
    Query recent CRITICAL audit findings.

    Args:
        limit: Maximum number of findings to retrieve

    Returns:
        Dictionary containing findings and pagination info
    """
    url = f"{BASE_URL}/audits"
    params = {
        "riskLevel": "CRITICAL",
        "size": limit,
        "sort": "createdAt,desc"
    }

    try:
        response = requests.get(url, params=params)
        response.raise_for_status()

        data = response.json()

        print(f"Total CRITICAL findings: {data['totalElements']}")
        print(f"Page {data['number'] + 1} of {data['totalPages']}")
        print("\nFindings:")

        for finding in data['content']:
            print("-" * 60)
            print(f"ID: {finding['id']}")
            print(f"SQL: {finding['sql']}")
            print(f"Risk Score: {finding['riskScore']}")
            print(f"Checker: {finding['checkerId']}")
            print(f"Message: {finding['message']}")
            print(f"Recommendation: {finding['recommendation']}")

        return data

    except requests.exceptions.RequestException as e:
        print(f"Error querying audit findings: {e}")
        return {}


def query_with_filters(
    sql_id: Optional[str] = None,
    risk_level: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    page: int = 0,
    size: int = 20
) -> Dict:
    """
    Query audit findings with custom filters.

    Args:
        sql_id: Filter by SQL identifier
        risk_level: Filter by risk level (CRITICAL, HIGH, MEDIUM, LOW)
        start_time: Start time for time range filter
        end_time: End time for time range filter
        page: Page number (zero-based)
        size: Page size

    Returns:
        Dictionary containing filtered findings
    """
    url = f"{BASE_URL}/audits"
    params = {
        "page": page,
        "size": size
    }

    if sql_id:
        params["sqlId"] = sql_id
    if risk_level:
        params["riskLevel"] = risk_level
    if start_time:
        params["startTime"] = start_time.isoformat()
    if end_time:
        params["endTime"] = end_time.isoformat()

    response = requests.get(url, params=params)
    response.raise_for_status()

    data = response.json()
    print(f"Filtered results: {data['totalElements']} findings")

    return data


def get_audit_by_id(report_id: str) -> Optional[Dict]:
    """
    Get a specific audit report by ID.

    Args:
        report_id: Audit report identifier

    Returns:
        Audit report dictionary or None if not found
    """
    url = f"{BASE_URL}/audits/{report_id}"

    try:
        response = requests.get(url)
        response.raise_for_status()

        report = response.json()

        print(f"Audit Report: {report['id']}")
        print(f"SQL: {report['sql']}")
        print(f"Risk Score: {report['riskScore']}")
        print(f"Metadata: {report.get('metadata', {})}")

        return report

    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 404:
            print(f"Audit report not found: {report_id}")
        else:
            print(f"Error retrieving audit report: {e}")
        return None


def get_dashboard_stats() -> Dict:
    """
    Get dashboard statistics.

    Returns:
        Dictionary containing dashboard statistics
    """
    url = f"{BASE_URL}/statistics/dashboard"

    response = requests.get(url)
    response.raise_for_status()

    stats = response.json()

    print("Dashboard Statistics:")
    print(f"Total Findings: {stats['totalFindings']}")
    print(f"Critical: {stats['criticalCount']}")
    print(f"High: {stats['highCount']}")
    print(f"Medium: {stats['mediumCount']}")
    print(f"Low: {stats['lowCount']}")

    if 'topRiskySql' in stats:
        print("\nTop Risky SQL Patterns:")
        for sql in stats['topRiskySql'][:5]:
            print(f"  - Score {sql['riskScore']}: {sql['sql'][:50]}...")

    return stats


def get_trend_stats(
    start_time: datetime,
    end_time: datetime,
    granularity: str = "DAY"
) -> List[Dict]:
    """
    Get trend statistics over time.

    Args:
        start_time: Start time for trend data
        end_time: End time for trend data
        granularity: Time granularity (HOUR, DAY, WEEK, MONTH)

    Returns:
        List of trend data points
    """
    url = f"{BASE_URL}/statistics/trends"
    params = {
        "startTime": start_time.isoformat(),
        "endTime": end_time.isoformat(),
        "granularity": granularity
    }

    response = requests.get(url, params=params)
    response.raise_for_status()

    trends = response.json()

    print(f"Trend data ({granularity}):")
    for point in trends:
        print(f"  {point['date']}: {point['count']} findings "
              f"({point.get('criticalCount', 0)} critical)")

    return trends


def main():
    """Main function demonstrating various API calls."""
    print("=== Querying Critical Findings ===")
    query_critical_findings(limit=5)

    print("\n=== Dashboard Statistics ===")
    get_dashboard_stats()

    print("\n=== Trend Statistics (Last 7 Days) ===")
    end_time = datetime.now()
    start_time = end_time - timedelta(days=7)
    get_trend_stats(start_time, end_time, granularity="DAY")

    print("\n=== Filtered Query ===")
    query_with_filters(
        risk_level="HIGH",
        start_time=start_time,
        size=10
    )


if __name__ == "__main__":
    main()

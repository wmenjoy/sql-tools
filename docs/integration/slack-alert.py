"""
Slack Alert Integration for SQL Audit Service

This script polls the SQL Audit Service API for critical findings
and sends alerts to Slack channels.

Requirements:
  - Python 3.7+
  - pip install requests slack-sdk

Environment Variables:
  - AUDIT_SERVICE_URL: Audit service URL (default: http://localhost:8090/api/v1)
  - SLACK_TOKEN: Slack bot token (required)
  - SLACK_CHANNEL: Slack channel ID (default: #sql-audit-alerts)
  - POLL_INTERVAL: Polling interval in seconds (default: 60)

Usage:
  export SLACK_TOKEN="xoxb-your-token"
  export SLACK_CHANNEL="#sql-audit-alerts"
  python slack-alert.py
"""

import os
import sys
import time
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Optional

import requests
from slack_sdk import WebClient
from slack_sdk.errors import SlackApiError


# Configuration
AUDIT_SERVICE_URL = os.getenv('AUDIT_SERVICE_URL', 'http://localhost:8090/api/v1')
SLACK_TOKEN = os.getenv('SLACK_TOKEN')
SLACK_CHANNEL = os.getenv('SLACK_CHANNEL', '#sql-audit-alerts')
POLL_INTERVAL = int(os.getenv('POLL_INTERVAL', '60'))  # seconds

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class SlackAlertService:
    """Service for sending SQL audit alerts to Slack."""

    def __init__(self, token: str, channel: str):
        """Initialize Slack client."""
        if not token:
            raise ValueError("SLACK_TOKEN environment variable is required")

        self.client = WebClient(token=token)
        self.channel = channel
        self.sent_alerts = set()  # Track sent alerts to avoid duplicates

    def send_alert(self, finding: Dict) -> bool:
        """
        Send alert to Slack for a critical finding.

        Args:
            finding: Audit finding dictionary

        Returns:
            True if alert sent successfully, False otherwise
        """
        finding_id = finding.get('id')

        # Skip if already sent
        if finding_id in self.sent_alerts:
            return False

        try:
            # Build Slack message with rich formatting
            blocks = self._build_message_blocks(finding)

            # Send message
            response = self.client.chat_postMessage(
                channel=self.channel,
                blocks=blocks,
                text=f"Critical SQL Issue: {finding.get('message', 'Unknown')}"  # Fallback text
            )

            # Mark as sent
            self.sent_alerts.add(finding_id)

            logger.info(f"Alert sent for finding {finding_id}")
            return True

        except SlackApiError as e:
            logger.error(f"Error sending Slack alert: {e.response['error']}")
            return False

    def _build_message_blocks(self, finding: Dict) -> List[Dict]:
        """Build Slack message blocks with rich formatting."""
        risk_score = finding.get('riskScore', 0)
        severity = finding.get('riskLevel', 'UNKNOWN')

        # Choose emoji and color based on severity
        emoji_map = {
            'CRITICAL': ':rotating_light:',
            'HIGH': ':warning:',
            'MEDIUM': ':yellow_circle:',
            'LOW': ':information_source:'
        }
        emoji = emoji_map.get(severity, ':question:')

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f"{emoji} Critical SQL Audit Finding",
                    "emoji": True
                }
            },
            {
                "type": "section",
                "fields": [
                    {
                        "type": "mrkdwn",
                        "text": f"*Severity:*\n{severity}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Risk Score:*\n{risk_score}/100"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Checker:*\n{finding.get('checkerId', 'Unknown')}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Time:*\n{finding.get('createdAt', 'Unknown')}"
                    }
                ]
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*SQL Statement:*\n```{finding.get('sql', 'N/A')}```"
                }
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Issue:*\n{finding.get('message', 'No description available')}"
                }
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Recommendation:*\n{finding.get('recommendation', 'No recommendation available')}"
                }
            },
            {
                "type": "divider"
            }
        ]

        # Add metadata if available
        metadata = finding.get('metadata')
        if metadata:
            metadata_text = '\n'.join([f"• *{k}:* {v}" for k, v in metadata.items()])
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Additional Details:*\n{metadata_text}"
                }
            })

        return blocks


class AuditServiceClient:
    """Client for SQL Audit Service API."""

    def __init__(self, base_url: str):
        """Initialize audit service client."""
        self.base_url = base_url

    def query_critical_findings(self, since: Optional[datetime] = None, limit: int = 100) -> List[Dict]:
        """
        Query critical findings from audit service.

        Args:
            since: Query findings created after this time
            limit: Maximum number of findings to retrieve

        Returns:
            List of audit findings
        """
        url = f"{self.base_url}/audits"
        params = {
            'riskLevel': 'CRITICAL',
            'size': limit,
            'sort': 'createdAt,desc'
        }

        if since:
            params['startTime'] = since.isoformat()

        try:
            response = requests.get(url, params=params, timeout=10)
            response.raise_for_status()

            data = response.json()
            return data.get('content', [])

        except requests.exceptions.RequestException as e:
            logger.error(f"Error querying audit service: {e}")
            return []


def main():
    """Main function to run the alert service."""
    logger.info("Starting SQL Audit Slack Alert Service")
    logger.info(f"Audit Service URL: {AUDIT_SERVICE_URL}")
    logger.info(f"Slack Channel: {SLACK_CHANNEL}")
    logger.info(f"Poll Interval: {POLL_INTERVAL} seconds")

    # Initialize clients
    try:
        slack_service = SlackAlertService(token=SLACK_TOKEN, channel=SLACK_CHANNEL)
        audit_client = AuditServiceClient(base_url=AUDIT_SERVICE_URL)
    except Exception as e:
        logger.error(f"Initialization failed: {e}")
        sys.exit(1)

    # Track last poll time
    last_poll = datetime.now() - timedelta(minutes=5)  # Look back 5 minutes initially

    # Main polling loop
    while True:
        try:
            logger.info("Polling for new critical findings...")

            # Query findings since last poll
            findings = audit_client.query_critical_findings(since=last_poll)

            if findings:
                logger.info(f"Found {len(findings)} critical findings")

                # Send alerts
                alerts_sent = 0
                for finding in findings:
                    if slack_service.send_alert(finding):
                        alerts_sent += 1

                logger.info(f"Sent {alerts_sent} new alerts")
            else:
                logger.info("No new critical findings")

            # Update last poll time
            last_poll = datetime.now()

            # Sleep until next poll
            time.sleep(POLL_INTERVAL)

        except KeyboardInterrupt:
            logger.info("Shutting down gracefully...")
            break

        except Exception as e:
            logger.error(f"Error in main loop: {e}", exc_info=True)
            time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()

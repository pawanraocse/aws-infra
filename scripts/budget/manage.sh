#!/bin/bash
set -e

# Configuration
PROJECT="saas-factory"
ENVIRONMENT="budget"
REGION="us-east-1"
BASTION_USER="ec2-user"
SSH_KEY="~/.ssh/pawankeys"

# Helper to get resources
get_ec2_id() {
  aws ec2 describe-instances \
    --filters "Name=tag:Project,Values=$PROJECT" "Name=tag:Environment,Values=$ENVIRONMENT" "Name=tag:Name,Values=$PROJECT-$ENVIRONMENT-bastion" "Name=instance-state-name,Values=running,stopped" \
    --query "Reservations[0].Instances[0].InstanceId" --output text --region $REGION
}

get_ec2_ip() {
  aws ec2 describe-instances \
    --filters "Name=tag:Project,Values=$PROJECT" "Name=tag:Environment,Values=$ENVIRONMENT" "Name=tag:Name,Values=$PROJECT-$ENVIRONMENT-bastion" "Name=instance-state-name,Values=running" \
    --query "Reservations[0].Instances[0].PublicIpAddress" --output text --region $REGION
}

get_rds_id() {
  aws rds describe-db-instances \
    --query "DBInstances[?contains(DBInstanceIdentifier, '$PROJECT-$ENVIRONMENT')].DBInstanceIdentifier | [0]" \
    --output text --region $REGION 2>/dev/null || echo ""
}

# Commands
case "$1" in
  status)
    echo "🔍 Checking status for $PROJECT-$ENVIRONMENT..."
    EC2_ID=$(get_ec2_id)
    if [ "$EC2_ID" == "None" ]; then
      echo "  EC2: Not found"
    else
      STATE=$(aws ec2 describe-instances --instance-ids $EC2_ID --query "Reservations[0].Instances[0].State.Name" --output text --region $REGION)
      echo "  EC2 ($EC2_ID): $STATE"
    fi

    RDS_ID=$(get_rds_id)
    if [ -n "$RDS_ID" ]; then
      RDS_STATE=$(aws rds describe-db-instances --db-instance-identifier $RDS_ID --query "DBInstances[0].DBInstanceStatus" --output text --region $REGION)
      echo "  RDS ($RDS_ID): $RDS_STATE"
    else
      echo "  RDS: Not found"
    fi
    ;;

  stop)
    echo "🛑 Stopping environment..."
    EC2_ID=$(get_ec2_id)
    if [ "$EC2_ID" != "None" ]; then
      aws ec2 stop-instances --instance-ids $EC2_ID --region $REGION
      echo "  Requested stop for EC2 ($EC2_ID)"
    fi

    RDS_ID=$(get_rds_id)
    if [ -n "$RDS_ID" ]; then
        # Check if already stopped
        RDS_STATE=$(aws rds describe-db-instances --db-instance-identifier $RDS_ID --query "DBInstances[0].DBInstanceStatus" --output text --region $REGION)
        if [ "$RDS_STATE" != "stopped" ] && [ "$RDS_STATE" != "stopping" ]; then
            aws rds stop-db-instance --db-instance-identifier $RDS_ID --region $REGION >/dev/null
            echo "  Requested stop for RDS ($RDS_ID)"
        else
            echo "  RDS is already $RDS_STATE"
        fi
    fi
    ;;

  start)
    echo "🚀 Starting environment..."
    EC2_ID=$(get_ec2_id)
    if [ "$EC2_ID" != "None" ]; then
      aws ec2 start-instances --instance-ids $EC2_ID --region $REGION
      echo "  Requested start for EC2 ($EC2_ID)"
    fi

    RDS_ID=$(get_rds_id)
    if [ -n "$RDS_ID" ]; then
         RDS_STATE=$(aws rds describe-db-instances --db-instance-identifier $RDS_ID --query "DBInstances[0].DBInstanceStatus" --output text --region $REGION)
         if [ "$RDS_STATE" == "stopped" ]; then
            aws rds start-db-instance --db-instance-identifier $RDS_ID --region $REGION >/dev/null
            echo "  Requested start for RDS ($RDS_ID)"
         else
            echo "  RDS is $RDS_STATE"
         fi
    fi
    ;;

  restart)
    echo "🔄 Rebooting EC2..."
    EC2_ID=$(get_ec2_id)
    [ "$EC2_ID" != "None" ] && aws ec2 reboot-instances --instance-ids $EC2_ID --region $REGION
    echo "  Requested reboot for EC2 ($EC2_ID)"
    ;;

  ssh)
    IP=$(get_ec2_ip)
    if [ "$IP" == "None" ] || [ -z "$IP" ]; then
      echo "❌ EC2 is not running or IP not found."
      exit 1
    fi
    echo "🔌 Connecting to $IP..."
    ssh -o StrictHostKeyChecking=no -i $SSH_KEY $BASTION_USER@$IP
    ;;

  *)
    echo "Usage: $0 {start|stop|restart|status|ssh}"
    exit 1
    ;;
esac

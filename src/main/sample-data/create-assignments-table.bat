@echo off
echo Creating ReviewerAssignments DynamoDB table...

aws dynamodb create-table --table-name ReviewerAssignments --attribute-definitions AttributeName=submissionId,AttributeType=S AttributeName=reviewerId,AttributeType=S --key-schema AttributeName=submissionId,KeyType=HASH --global-secondary-indexes "[{\"IndexName\":\"reviewerId-index\",\"KeySchema\":[{\"AttributeName\":\"reviewerId\",\"KeyType\":\"HASH\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 --region us-east-2

echo Waiting for table to become active...
aws dynamodb wait table-exists --table-name ReviewerAssignments --region us-east-2

echo Done! ReviewerAssignments table is ready.

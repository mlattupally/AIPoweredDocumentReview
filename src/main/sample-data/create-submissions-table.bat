@echo off
echo Creating HealthcareSubmissions DynamoDB table...

aws dynamodb create-table --table-name HealthcareSubmissions --attribute-definitions AttributeName=submissionId,AttributeType=S AttributeName=status,AttributeType=S --key-schema AttributeName=submissionId,KeyType=HASH --global-secondary-indexes "[{\"IndexName\":\"status-index\",\"KeySchema\":[{\"AttributeName\":\"status\",\"KeyType\":\"HASH\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 --region us-east-2

echo Waiting for table to become active...
aws dynamodb wait table-exists --table-name HealthcareSubmissions --region us-east-2

echo Done! HealthcareSubmissions table is ready.

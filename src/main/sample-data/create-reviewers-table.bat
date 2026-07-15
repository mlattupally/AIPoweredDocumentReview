@echo off
echo Creating HealthcareReviewers DynamoDB table...

aws dynamodb create-table --table-name HealthcareReviewers --attribute-definitions AttributeName=reviewerId,AttributeType=S AttributeName=reviewerLevel,AttributeType=S --key-schema AttributeName=reviewerId,KeyType=HASH --global-secondary-indexes "[{\"IndexName\":\"reviewerLevel-index\",\"KeySchema\":[{\"AttributeName\":\"reviewerLevel\",\"KeyType\":\"HASH\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 --region us-east-2

echo Waiting for table to become active...
aws dynamodb wait table-exists --table-name HealthcareReviewers --region us-east-2

echo Done! Table is ready.

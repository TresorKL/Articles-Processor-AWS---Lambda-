# Articles Processor (AWS - Lambda):

## Description: This project constitutes the lambda function of the above project. It is triggered by an SQS queue and receives text and images (that are already stored in the S3 bucket) then it analyzes text using AWS Comprehend and images using AWS Rekognition after the analysis, this function sends the outcome as a message to another SQS queue that will be later consumed by the above project.

## Technologies and Tools: 
Java (with Mavean), AWS (comprehend and rekognition)

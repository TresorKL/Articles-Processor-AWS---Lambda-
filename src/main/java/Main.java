import com.amazonaws.handlers.RequestHandler;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;



import java.util.List;

public class Main {

    public void handleRequest(SQSEvent sqsEvent) throws JsonProcessingException {


        System.out.println("POST PROCESSING HAS STARTED ********* ");


        // Set up your AWS credentials and region
      
        Region region = Region.US_EAST_1; // Choose the appropriate region


        //BlogTechPostProcessingQueue
        String inputQueueUrl = "https://sqs.us-east-1.amazonaws.com/889688243638/BlogTechPostProcessingQueue";

        //PostAnalysisResultQueue
        String outputQueueUrl = "https://sqs.us-east-1.amazonaws.com/889688243638/PostAnalysisResultQueue";

        // Create SQS clients
        SqsClient sqsClient = SqsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())// Using default credential provider because this code will be running on aws lambda function
                .build();

        // Consume message from input queue
        ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                        .queueUrl(inputQueueUrl)
                        .maxNumberOfMessages(1)
                        .build()
        );


        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            String messageBody = message.getBody();


            ObjectMapper objectMapper = new ObjectMapper();


            JsonNode jsonNode = objectMapper.readTree(messageBody);


            // Access specific fields
            String id = jsonNode.get("id").asText();
            String blogId = jsonNode.get("blogId").asText();
            String title = jsonNode.get("title").asText();
            String textContent = jsonNode.get("textContent").asText();
            String metaContentName = jsonNode.get("metaContents").get("name").asText();
            String downloadUrl = jsonNode.get("metaContents").get("download_url").asText();
//

            String resultOfTextAnalyze = analyzeTextContent(  region, title + " " + textContent);


            System.out.println("TEXT RESULT: " + resultOfTextAnalyze);

            List<ModerationLabel> moderationLabels = analyzeImage( region, metaContentName);

            System.out.println("IMAGE RESULT: " + moderationLabels.isEmpty());

            String analysisResult = "";


            if (!resultOfTextAnalyze.equalsIgnoreCase("NEGATIVE") && moderationLabels.isEmpty()) {
                analysisResult = "VALID POST";

            } else if (resultOfTextAnalyze.equalsIgnoreCase("NEGATIVE") && !moderationLabels.isEmpty()) {

                analysisResult = "INVALID POST (contains Image and text that judged as negative contents)";

            } else if (resultOfTextAnalyze.equalsIgnoreCase("NEGATIVE") || !moderationLabels.isEmpty()) {

                analysisResult = "INVALID POST (contains either Image or text one is judged as negative content)";

            }
            //System.out.println(analysisResult);


            MetaContent metaContent = MetaContent.builder()
                    .name(metaContentName)
                    .download_url(downloadUrl)
                    .build();


            PostObjectSQS postObjectSQS = PostObjectSQS.builder()
                    .id(id)
                    .blogId(blogId)
                    .textContent(textContent)
                    .title(title)
                    .metaContents(metaContent)
                    .analysisResult(analysisResult)
                    .build();


            String postObjectJson = objectMapper.writeValueAsString(postObjectSQS);


           // deleteMessage(message, sqsClient, inputQueueUrl); //*********** //

            //       Publish message to output queue
            sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(outputQueueUrl)
                            .messageBody(postObjectJson)
                            .build()
            );

            System.out.println("Published processed message to output queue.");
     }
//


        }


        private static void deleteMessage (Message message, SqsClient sqsClient, String queueUrl){
            // Delete the message from the queue
            DeleteMessageResponse deleteMessageResponse = sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
            );
            System.out.println("Message deleted. Receipt handle: " + message.receiptHandle());
        }

//AwsBasicCredentials credentials,
        public static String analyzeTextContent ( Region region, String textToAnalyze){

            ComprehendClient comprehendClient = ComprehendClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();


            // Use Amazon Comprehend to detect sentiment
            DetectSentimentResponse sentimentResponse = comprehendClient.detectSentiment(
                    DetectSentimentRequest.builder()
                            .text(textToAnalyze)
                            .languageCode("en")
                            .build()
            );

            return sentimentResponse.sentiment().toString();
        }

        //AwsBasicCredentials credentials,
        public static List<ModerationLabel> analyzeImage (Region region, String
        objectKey){

            // Create an Amazon Rekognition client
            RekognitionClient rekognitionClient = RekognitionClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    // .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            // Specify the S3 bucket and key for the image you want to analyze
            String bucketName = "techblog-storage";


            // Create a Moderation Label detection request
            DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
                    .image(Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(objectKey).build()).build())
                    .build();

            // Call the DetectModerationLabels operation
            DetectModerationLabelsResponse response = rekognitionClient.detectModerationLabels(request);

            rekognitionClient.close();

            // Process the response
            return response.moderationLabels();

            // Close the Rekognition client
        }

}

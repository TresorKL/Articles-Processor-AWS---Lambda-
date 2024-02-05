

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostObjectSQS {

    private String id;
    private String blogId;
    private String title;
    private String textContent;
    @JsonProperty("metaContents")
    private MetaContent metaContents;
    private String analysisResult;




}

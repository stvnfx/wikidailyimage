package dev.sf13.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
public interface DescriptionAiService {

    @SystemMessage("You are a helpful assistant that summarizes text.")
    @UserMessage("Shorten the following paragraph into a short 12 word or so sentence summary: {text}")
    String summarize(String text);
}

package ru.balladali.mashabot.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.balladali.mashabot.core.handlers.message.ConversationHandler;

@RestController
@RequestMapping("/api/v1/masha")
public class ConversationController {

  @Autowired
  private ConversationHandler conversationHandler;


  @RequestMapping(
      path = "/answer",
      method = RequestMethod.POST,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public String answer(@RequestParam("chatId") String chatId,
      @RequestParam("message") String message) {
    return conversationHandler.getAnswer(message, chatId);
  }
}

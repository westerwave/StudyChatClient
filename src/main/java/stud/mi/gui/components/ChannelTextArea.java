package stud.mi.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.VerticalLayout;

import stud.mi.client.ChatClient;
import stud.mi.gui.ChatView;
import stud.mi.message.Message;

public class ChannelTextArea extends Panel
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelTextArea.class);
    private static final long serialVersionUID = -744672565761458560L;
    private final List<Label> messages = new ArrayList<>();
    private final VerticalLayout verticalLayout = new VerticalLayout();

    public ChannelTextArea()
    {
        super();
        this.setContent(this.verticalLayout);
        this.setHeight("100%");
    }

    public void init()
    {
        this.getClient().setChatMessageListener(this::addMessage);
        this.getClient().setChannelJoinListener(this::clearMessages);
    }

    public void addMessage(final Message msg)
    {
        LOGGER.debug("Add Message from User {} and Content {}", msg.getUserName(), msg.getMessage());
        final String message = String.format("<b>%s</b>: %s%s", msg.getUserName(), msg.getMessage(), "<br/>");
        final Label messageLabel = new Label(message);
        messageLabel.setContentMode(ContentMode.HTML);
        messageLabel.setWidth("100%");
        this.messages.add(messageLabel);
        this.verticalLayout.addComponent(messageLabel);
        final int randomOffset = new Random().nextInt(10) - 5;
        this.setScrollTop(Short.MAX_VALUE + randomOffset);
    }

    private void clearMessages()
    {
        LOGGER.debug("Clear all messages");
        this.verticalLayout.removeAllComponents();
    }

    private ChatClient getClient()
    {
        final ChatView parent = (ChatView) this.getParent();
        return parent.getClient();
    }
}

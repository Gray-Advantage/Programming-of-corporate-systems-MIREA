package space.grayt.teremok.auth.messaging;

import space.grayt.teremok.events.UserCreatedEvent;

public interface UserCreatedEventPublisher {

    void publish(UserCreatedEvent event);
}

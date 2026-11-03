package com.alisa.notification.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailNotificationAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailNotificationAutoConfiguration.class));

    @Test void libraryDoesNotCreateInfrastructureWhenNoMailTransportIsConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MailNotificationSender.class));
    }
    @Test void configuredTransportCanSendAnyApplicationsMessage() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        runner.withBean(JavaMailSender.class, () -> smtp)
                .withPropertyValues("spring.mail.username=sender@example.test")
                .run(context -> {
                    context.getBean(MailNotificationSender.class).send("recipient@example.test", "A project notification", "Body");
                    var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
                    verify(smtp).send(message.capture());
                    assertThat(message.getValue().getFrom()).isEqualTo("sender@example.test");
                    assertThat(message.getValue().getTo()).containsExactly("recipient@example.test");
                    assertThat(message.getValue().getSubject()).isEqualTo("A project notification");
                });
    }
    @Test void consumingProjectsCanReplaceTheTransport() {
        MailNotificationSender custom = mock(MailNotificationSender.class);
        runner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withBean(MailNotificationSender.class, () -> custom)
                .run(context -> assertThat(context.getBean(MailNotificationSender.class)).isSameAs(custom));
    }
}

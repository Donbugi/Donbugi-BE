package hongik.finEdu.trading.stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class TradingStreamConfig {

    @Bean
    public RedisMessageListenerContainer tradingEventListenerContainer(RedisConnectionFactory factory,
                                                                       TradingEventStream stream) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(stream, new ChannelTopic(TradingEventStream.CHANNEL));
        return container;
    }
}

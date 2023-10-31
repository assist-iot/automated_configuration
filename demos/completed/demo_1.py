import uuid

from demos.utils.commons import post_reaction_model, consumer_from_topic, producer
from demos.utils.models import multistep_conf_reaction_1, multistep_conf_reaction_2, ms_register_updated_resource, \
    ms_register_resource

resources_topic_name = "resources-topic-1"


def create_reactions():
    print(post_reaction_model(8081, multistep_conf_reaction_1))
    print(post_reaction_model(8081, multistep_conf_reaction_2))


def get_interesting_messages(consumer, configuration_step):
    msg_pack = consumer.poll(timeout_ms=60_000)
    messages = [message.value for messages in [batch for (partition, batch) in msg_pack.items()] for message in
                messages]
    interesting_message = [content for content in messages if
                           content['trigger']['content']['id'] == 'agv_1' and content['trigger']['content']['labels'][
                               'configuration_step'] == configuration_step]
    return interesting_message


def simulate_connector():
    # Register Device
    prod = producer()
    prod.send(resources_topic_name, ms_register_resource, key=str(uuid.uuid4()))

    consumer = consumer_from_topic("agv_1")
    interesting_messages = get_interesting_messages(consumer, "not_configured")
    if len(interesting_messages) > 0:
        print(interesting_messages)
        print("First step completed!")
        prod.send(resources_topic_name, ms_register_updated_resource, key=str(uuid.uuid4()))
        interesting_messages_second_step = get_interesting_messages(consumer, "map_downloaded")
        if len(interesting_messages_second_step) > 0:
            print(interesting_messages_second_step)
            print("Second step completed!")


def main():
    create_reactions()
    simulate_connector()


if __name__ == "__main__":
    main()

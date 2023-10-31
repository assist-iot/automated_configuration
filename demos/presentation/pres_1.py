import uuid

from demos.utils.commons import post_reaction_model, producer, consumer_from_topic
from demos.utils.models import multistep_conf_reaction_1, ms_register_resource, ms_register_updated_resource, \
    multistep_conf_reaction_2

resources_topic_name = "resources-topic-1"
port = 8081


def create_reactions():
    print(post_reaction_model(port, multistep_conf_reaction_1))
    print(post_reaction_model(port, multistep_conf_reaction_2))


def get_interesting_messages(consumer, configuration_step):
    msg_pack = consumer.poll(timeout_ms=60_000)
    messages = extract_messages_from_kafka_batch(msg_pack)
    interesting_messages = [content for content in messages if
                            content['trigger']['content']['id'] == 'agv_1' and
                            content['trigger']['content']['labels'][
                                'configuration_step'] == configuration_step]
    return interesting_messages


def extract_messages_from_kafka_batch(msg_pack):
    return [message.value for messages in [batch for (partition, batch) in msg_pack.items()] for message in
            messages]


def simulate_connector():
    prod = producer()
    prod.send(resources_topic_name, ms_register_resource, key=str(uuid.uuid4()))

    consumer = consumer_from_topic("agv_1")
    ims = get_interesting_messages(consumer, "not_configured")
    if len(ims) > 0:
        print(ims)
        print("First step completed!")
        print("Map downloaded!")
        prod.send(resources_topic_name, ms_register_updated_resource, key=str(uuid.uuid4()))
        ims_second_step = get_interesting_messages(consumer, "map_downloaded")
        if len(ims_second_step) > 0:
            print(ims_second_step)
            print("Yay, configuration finished!")


def main():
    create_reactions()
    simulate_connector()


if __name__ == "__main__":
    main()

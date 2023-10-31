import uuid

from demos.utils.commons import publish_custom_message, post_reaction_model, producer
from demos.utils.models import conditional_reaction, parking_zone_resource

resources_topic_name = "resources-topic-3"
port = 8083


def create_reaction():
    print(post_reaction_model(port, conditional_reaction))


def register_parking_zone_dispatcher():
    prod = producer()
    prod.send(resources_topic_name, parking_zone_resource, key=str(uuid.uuid4()))


def main():
    create_reaction()
    publish_custom_message(resources_topic_name, "fire")
    register_parking_zone_dispatcher()
    publish_custom_message(resources_topic_name, "fire")


if __name__ == "__main__":
    main()

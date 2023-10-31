import uuid

from demos.utils.commons import post_reaction_model, publish_custom_message, producer
from demos.utils.models import conditional_reaction, parking_zone_resource

resources_topic_name = "resources-topic-3"


def create_reaction():
    print(post_reaction_model(8083, conditional_reaction))


def register_parking_zone_dispatcher():
    prod = producer()
    prod.send(resources_topic_name, parking_zone_resource, key=str(uuid.uuid4()))
    prod.close()
    pass


def main():
    create_reaction()
    publish_custom_message(resources_topic_name, "fire")
    register_parking_zone_dispatcher()
    publish_custom_message(resources_topic_name, "fire")


if __name__ == "__main__":
    main()

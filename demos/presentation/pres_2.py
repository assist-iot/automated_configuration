import uuid

from demos.utils.commons import producer, post_requirements_model, post_reaction_model, publish_custom_message
from demos.utils.models import high_priority_cargo_requirements, \
    medium_priority_cargo_requirements, low_priority_cargo_requirements, important_reaction, \
    important_traffic_resources_agv, important_traffic_resources_rtg, important_traffic_resources_agv_remove

resources_topic_name = "resources-topic-2"
port = 8082


def create_requirements():
    print(post_requirements_model(port, high_priority_cargo_requirements))
    print(post_requirements_model(port, medium_priority_cargo_requirements))
    print(post_requirements_model(port, low_priority_cargo_requirements))


def create_reactions():
    print(post_reaction_model(port, important_reaction))


def register_all_agv():
    prod = producer()
    for agv in important_traffic_resources_agv:
        prod.send(resources_topic_name, agv, key=str(uuid.uuid4()))
    prod.close()


def register_all_rtgs():
    prod = producer()
    for rtg in important_traffic_resources_rtg:
        prod.send(resources_topic_name, rtg, key=str(uuid.uuid4()))
    prod.close()


def deregister_agvs(count: int):
    prod = producer()
    for remove_agv in important_traffic_resources_agv_remove(count):
        prod.send(resources_topic_name, remove_agv, key=str(uuid.uuid4()))
    prod.close()


def main():
    create_requirements()
    register_all_agv()
    register_all_rtgs()
    create_reactions()
    deregister_agvs(5)
    publish_custom_message(resources_topic_name, "critical_event")


if __name__ == "__main__":
    main()

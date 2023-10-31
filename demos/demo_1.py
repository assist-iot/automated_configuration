import uuid

from demos.commons import post_reaction_model, producer

resources_topic_name = "resources-topic-3"


multistep_conf_reaction_1 = {
    "reactionId": "multi_step_configuration_1",
    "filterExpression": {
        "messageType": "ResourceWithLabelIsAvailable",
        "labelKey": "configuration_step",
        "labelValue": "not_configured"
    },
    "action": {
        "topic": "agv_1",
        "message": "map_download_link: http://proper_address.com"
    }
}

multistep_conf_reaction_2 = {
    "reactionId": "multi_step_configuration_2",
    "filterExpression": {
        "messageType": "ResourceWithLabelIsAvailable",
        "labelKey": "configuration_step",
        "labelValue": "map_downloaded"
    },
    "action": {
        "topic": "agv_1",
        "message": "ask_assigner_for_zone. assigner_location: ABC"
    }
}

register_resource = {
    "messageType": "RegisterResource",
    "resource": {
        "id": "agv_1",
        "labels": {
            "configuration_step": "not_configured"
        }
    }
}


def create_reactions():
    print(post_reaction_model(8083, multistep_conf_reaction_1))
    print(post_reaction_model(8083, multistep_conf_reaction_2))


def produce_kafka_messages():
    prod = producer()
    prod.send(resources_topic_name, register_resource, key=str(uuid.uuid4()))


def main():
    create_reactions()
    produce_kafka_messages()
    pass


if __name__ == "__main__":
    main()

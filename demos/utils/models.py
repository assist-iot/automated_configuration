# First Scenario
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

ms_register_resource = {
    "messageType": "RegisterResource",
    "resource": {
        "id": "agv_1",
        "labels": {
            "configuration_step": "not_configured"
        }
    }
}

ms_register_updated_resource = {
    "messageType": "RegisterResource",
    "resource": {
        "id": "agv_1",
        "labels": {
            "configuration_step": "map_downloaded"
        }
    }
}

# Second Scenario
important_traffic_resources_agv = map(lambda id_num: {
    "messageType": "RegisterResource",
    "resource": {
        "id": f"agv_{id_num}",
        "labels": {
            "resource_type": "agv"
        }
    }
}, [id_num for id_num in range(1, 13)])


def important_traffic_resources_agv_remove(count: int):
    return map(lambda id_num: {
        "messageType": "DeregisterResource",
        "resource": {
            "id": f"agv_{id_num}",
            "labels": {
                "resource_type": "agv"
            }
        }
    }, [id_num for id_num in range(1, count + 1)])


important_traffic_resources_rtg = map(lambda id_num: {
    "messageType": "RegisterResource",
    "resource": {
        "id": f"rtg_{id_num}",
        "labels": {
            "resource_type": "rtg"
        }
    }
}, [id_num for id_num in range(1, 4)])

high_priority_cargo_requirements = {
    "id": "high_priority_cargo_handling",
    "labels": {},
    "requirements": [
        {
            "id": "rtg_1",
            "exclusive": True
        },
        {
            "labelKey": "resource_type",
            "labelValue": "agv",
            "count": 5,
            "exclusive": True
        }
    ],
    "weight": 10.0
}

medium_priority_cargo_requirements = {
    "id": "medium_priority_cargo_handling",
    "labels": {},
    "requirements": [
        {
            "id": "rtg_2",
            "exclusive": True
        },
        {
            "labelKey": "resource_type",
            "labelValue": "agv",
            "count": 4,
            "exclusive": True
        }
    ],
    "weight": 7.0
}

low_priority_cargo_requirements = {
    "id": "low_priority_cargo_handling",
    "labels": {

    },
    "requirements": [
        {
            "id": "rtg_3",
            "exclusive": True
        },
        {
            "labelKey": "resource_type",
            "labelValue": "agv",
            "count": 3,
            "exclusive": True
        }
    ],
    "weight": 5.0
}

important_reaction = {
    "reactionId": "keep_critical_traffic_running",
    "filterExpression": {
        "messageType": "CustomMessageContent",
        "content": "critical_event"
    },
    "action": "KeepHighestWeightFunctionalities"
}

# Third Scenario
conditional_reaction = {
    "reactionId": "go_to_parking",
    "filterExpression": {
        "messageType": "CustomMessageContent",
        "content": "fire"
    },
    "action": {
        "conditionalCheck": {
            "condition_name": "ContainsResourceWithId",
            "id": "parking_zone_dispatcher"
        },
        "action": {
            "topic": "resource",
            "message": "go_to_parking"
        },
        "fallback": {
            "topic": "112",
            "message": "HELP!"
        }
    }
}

parking_zone_resource = {
    "messageType": "RegisterResource",
    "resource": {
        "id": f"parking_zone_dispatcher",
        "labels": {}
    }
}

from json import loads, dumps

import requests
from kafka import KafkaConsumer, KafkaProducer

bootstrap_servers = ['localhost:29092']


def consumer_from_topic(topic_name):
    consumer = KafkaConsumer(
        topic_name,
        bootstrap_servers=bootstrap_servers,
        auto_offset_reset='earliest',
        enable_auto_commit=True,
        group_id='my-group',
        value_deserializer=lambda x: loads(x.decode('utf-8')))
    return consumer


def producer():
    return KafkaProducer(bootstrap_servers=bootstrap_servers,
                         value_serializer=lambda x:
                         dumps(x).encode('utf-8'),
                         key_serializer=lambda x:
                         dumps(x).encode('utf-8'))


def post_reaction_model(port: int, reaction_model):
    reaction_url = f"http://localhost:{port}/reaction-model"
    response = requests.post(url=reaction_url, data=dumps(reaction_model))
    return response.content


def post_requirements_model(port: int, requirements_model):
    requirements_url = f"http://localhost:{port}/requirements-model"
    response = requests.post(url=requirements_url, data=dumps(requirements_model))
    return response.content

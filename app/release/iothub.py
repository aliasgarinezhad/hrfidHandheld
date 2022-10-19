import requests
import os
import sys
import time
from azure.iot.hub import IoTHubRegistryManager
from azure.iot.hub.models import Twin, TwinProperties
import json

def upload_update_file(version: str):
    file_name = 'app-debug-' + version + '.apk'
    os.rename("app-release.apk", file_name)
    file = open(file_name, 'rb')
    print('uploading ' + file_name + ' ...\n')
    response = requests.post('http://rfid-api.avakatan.ir/apk', files={file_name: file})
    print(response.text)
    input('uploading finished\n')


def update_devices(location: str, version: str) -> int:
    connection_string = 'HostName=rfid-frce.azure-devices.net;SharedAccessKeyName=service;SharedAccessKey' \
                        '=jmWPMGcrI7aExNls/n+IX9GB7A/VGkNd7oa2OZkyWK8= '
    device_manager = IoTHubRegistryManager(connection_string)
    devices: list[Twin] = device_manager.get_devices()
    number_of_devices = 0
    for device in devices:
        device_string = str(device).replace("'", '"')
        device_properties_short = json.loads(device_string[0:device_string.find(', "generation_id":')] + '}')

        if str(device_properties_short['device_id']).startswith("rfh"):
            device_id = device_properties_short['device_id']
            device_twin: Twin = device_manager.get_twin(device_id)
            device_twin_properties: TwinProperties = device_twin.properties
            if "location" in device_twin_properties.reported.keys():
                if device_twin_properties.reported['location'].__str__().__contains__(location):
                    desired = {
                        "appVersion": version
                    }
                    new_twin = Twin()
                    new_twin.properties = TwinProperties(desired=desired)
                    device_manager.update_twin(device_id, new_twin)
                    number_of_devices += 1
    return number_of_devices

upload_update_file("3.0.4")
update_devices("68", "3.0.4")

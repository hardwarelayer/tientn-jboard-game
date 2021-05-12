import requests
import json

url = "https://bravenewcoin.p.rapidapi.com/asset"

querystring = {
       #"symbol": "BTC",
       "type": "CRYPTO",
       "status":"ACTIVE"}

headers = {
    'x-rapidapi-key': "698df107e9msh4ee12e10d127188p124603jsn2de791cd9000",
    'x-rapidapi-host': "bravenewcoin.p.rapidapi.com"
    }

response = requests.request("GET", url, headers=headers, params=querystring)

obj_json = json.loads(response.text)
print(json.dumps(obj_json, indent=True))
print("Total assets: {}".format(len(obj_json['content'])))

import os
import sys
import requests

print("get asset ID from UUID, as listed from list_asset.py")
if len(sys.argv) < 2:
  print("please supply asset_uuid_id, eg: f1ff77b6-3ab4-4719-9ded-2fc7e71cff1f for BTC")
  exit()

asset_uuid = sys.argv[1]

url = "https://bravenewcoin.p.rapidapi.com/asset/{}".format(asset_uuid)

headers = {
    'x-rapidapi-key': "698df107e9msh4ee12e10d127188p124603jsn2de791cd9000",
    'x-rapidapi-host': "bravenewcoin.p.rapidapi.com"
    }

response = requests.request("GET", url, headers=headers)

print(response.text)

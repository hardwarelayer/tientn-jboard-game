import os
import sys
import requests

if len(sys.argv) < 3:
  print("please supply bearer_token(from get_token.py) asset_id")
  exit()

bearer_token = sys.argv[1]
asset_id = sys.argv[2]

url = "https://bravenewcoin.p.rapidapi.com/market-cap"

querystring = {
               "assetId":asset_id
              }

headers = {
    'authorization': "Bearer {}".format(bearer_token),
    'x-rapidapi-key': "698df107e9msh4ee12e10d127188p124603jsn2de791cd9000",
    'x-rapidapi-host': "bravenewcoin.p.rapidapi.com"
    }

response = requests.request("GET", url, headers=headers, params=querystring)

print(response.text)

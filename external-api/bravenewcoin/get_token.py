import requests

url = "https://bravenewcoin.p.rapidapi.com/oauth/token"

payload = """
    {
    "audience": "https://api.bravenewcoin.com",
    "client_id": "oCdQoZoI96ERE9HY3sQ7JmbACfBf55RY",
    "grant_type": "client_credentials"
    }
"""
headers = {
    'content-type': "application/json",
    'x-rapidapi-key': "698df107e9msh4ee12e10d127188p124603jsn2de791cd9000",
    'x-rapidapi-host': "bravenewcoin.p.rapidapi.com"
    }

response = requests.request("POST", url, data=payload, headers=headers)

print(response.text)

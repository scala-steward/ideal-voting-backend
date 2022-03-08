# ideal-voting-backend

[![Build Status][Badge-GitHubActions]][Link-GitHubActions]

Backend for IDEALvoting

To start the server:
```
> docker-compose -f docker-compose.yml -f docker-compose.local.yml up -d
> ./sbt idealVotingServer/run
```

## API

[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]

[Documentation](https://s01.oss.sonatype.org/service/local/repositories/releases/archive/cz/idealiste/ideal-voting-contract_2.13/0.0.18/ideal-voting-contract_2.13-0.0.18-javadoc.jar/!/openapi/index.html)

### Create election

`/v1/election` `POST`

Example request:
```json5
{
  "title": "My Election",
  "description": "Election description", // optional
  "admin": "john.doe@gmail.com",
  "options": [
    {
      "title": "Option A",
      "description": "This is option A" // optional
    },
    {
      "title": "Option B",
      "description": "This is option B" // optional
    }
  ],
  "voters": [
    "alice@yahoo.com",
    "Bob <bob@mail.com>"
  ]
}
```
Example response:
```json5
{
  "links": [
    {
      "href": "/v1/election/admin/my-election/asdfasdfasdf",
      "rel": "election-view-admin",
      "method": "GET",
      "parameters": {
        "titleMangled": "my-election",
        "token": "asdfasdfasdf"
      }
    }
  ]
}
```

### Cast a vote

`/v1/election/<election-title-mangled>/<token>` `POST`

Example request:

`/v1/election/my-election/qwerqwerqwer`
```json5
{
  "preferences": [
    1,
    0
  ]
}
```

Example response:
```json5
{
  "links": [
    {
      "href": "/v1/election/my-election/qwerqwerqwer",
      "rel": "election-view",
      "method": "GET",
      "parameters": {
        "titleMangled": "my-election",
        "token": "qwerqwerqwer"
      }
    }
  ]
}
```

### End election

`/v1/election/admin/<election-title-mangled>/<token>` `POST`

Example request:

`/v1/election/admin/my-election/qwerasdfzxcv`

_empty body_

Example response:
```json5
{
  "links": [
    {
      "href": "/v1/election/admin/my-election/qwerasdfzxcv",
      "rel": "election-view-admin",
      "method": "GET",
      "parameters": {
        "titleMangled": "my-election",
        "token": "qwerasdfzxcv"
      }
    }
  ]
}
```


[Link-GitHubActions]: https://github.com/Idealiste-cz/ideal-voting-backend/actions/workflows/release.yml?query=branch%3Amaster "GitHub Actions link"
[Badge-GitHubActions]: https://github.com/Idealiste-cz/ideal-voting-backend/actions/workflows/release.yml/badge.svg "GitHub Actions badge"

[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/cz/idealiste/ideal-voting-contract_2.13/ "Sonatype Releases link"
[Badge-SonatypeReleases]: https://maven-badges.herokuapp.com/maven-central/cz.idealiste/ideal-voting-contract_2.13/badge.svg "Sonatype Releases badge"

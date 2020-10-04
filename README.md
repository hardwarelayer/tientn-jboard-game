## TienTN JBoard Game

A java game using Swing and Docker instances. Territories buidling and board based, turn based war games.

I have played a lot of strategy series, from Civilization and EU4, IronHearts, Victoria ... But they are over complicated, so I think it's time to create a version of my own.

---------------

### Notes:

I make a board game based on TripA, this game will be more 'territory building' than the original.

Other feature: with multiple language support

I will try to make this game still compatible with the base TripA, for any updates.

My branch is always on feature-tientn-j-lang

This branch 'master' will always be used to keep up with newer releases of the core TripA releases, except this file.

Good luck with the game :)

Current version: 0.1

Current core release: 2.3.21874

History of core update:

2.3.21874 2020/10/04


### Porting:

This is my porting of tripleA to MacOSX Catalina, JDK15, Docker 2400(48506), gradle 6.6.1

Changes for porting: build parameters, tool and script environment ...


### Features:

2020-10-04: load tooltip.properties with UTF-8


### Running locally:

    %cd database

    %./start_docker_db

    %docker ps

    %./load_sample_data

    %./connect_to_docker_db

    %cd ..

    %./gradlew clean

    %./gradlew spotlessApply

    %./gradlew lobby-server:run

    %./gradlew game-headed:run

#### Setting game client:

    Go to Engine Reference

    Go to testing

      Change Lobby URI Override: -> Localserver (http://localhost:8080)

      Save and close

    Rerun game headed again

------------------

## License

This project is licensed under the terms of the
[GNU General Public License v3.0 with additional permissions](/LICENSE).

Copyright (C) 2001-2019 TripleA contributors.

This program is free software; you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License below for more details.

#### Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with any
of the following libraries (or a modified version of those libraries), containing
parts covered by the terms of the library's associated license, the licensors of
this Program grant you additional permission to convey the resulting work.

Library | Group ID | Artifact ID | SPDX License ID
:-- | :-- | :-- | :--
Jakarta Mail | com.sun.mail | jakarta.mail | GPL-2.0-only

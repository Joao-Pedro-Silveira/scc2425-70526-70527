Location of Hibernate and .env file: scc2425-tukano/src/main/resources

    Environmental Variables (from .env file):
    NOSQL:  true -> use Cosmos DB NoSQL
            false -> use Cosmos DB PostGree
            
    Cache:  true -> use Redis Cache
            false -> don't use Redis Cache

If running the application localy or from a diferent Azure service:

    In the file scc2425-tukano/src/main/java/tukano/impl/rest/TukanoRestServer.java change line 38 (serverURI) to represent the new server Uri (this is used to create the blob URL).

If using a diferent DB or Storage account:

    Change the .env file and  hibernate.cfg.xml file to have the correct connection to the new services.

User class was altered to have an extra 'id' parameter (this was done because of azure's containers require an extra '/id' table parameter), please add this parameter to the json body when creating a user object, userId and id **MUST** be the same.

The boolean values like and follow methods were changed to be query parameters.

Group Members:

    André Branco nº 70526
    João Silveira nº 70527

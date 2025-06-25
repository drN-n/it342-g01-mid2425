package com.contactintegration.google.Services;

import com.contactintegration.google.Models.Contact;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.*;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleContactsService {

    private PeopleService peopleService;

    // Initialize the PeopleService using OAuth2 token
    public void initializePeopleService(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new IllegalStateException("OAuth2AuthorizedClient is missing or invalid.");
        }

        GoogleCredential credential = new GoogleCredential()
                .setAccessToken(authorizedClient.getAccessToken().getTokenValue());

        this.peopleService = new PeopleService.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("ContactIntegration")
                .build();
    }

    private PeopleService getPeopleService() {
        if (peopleService == null) {
            throw new IllegalStateException("PeopleService not initialized. Call initializePeopleService first.");
        }
        return peopleService;
    }

    // Create a new Google contact and return its resource name
    public String createGoogleContact(Contact contact) throws IOException {
        Person person = new Person();

        if (contact.getName() != null) {
            person.setNames(List.of(new Name().setGivenName(contact.getName())));
        }
        if (contact.getEmail() != null) {
            person.setEmailAddresses(List.of(new EmailAddress().setValue(contact.getEmail())));
        }
        if (contact.getPhoneNumber() != null) {
            person.setPhoneNumbers(List.of(new PhoneNumber().setValue(contact.getPhoneNumber())));
        }

        Person createdPerson = getPeopleService().people().createContact(person).execute();
        return createdPerson.getResourceName();
    }

    // Update an existing Google contact
    public void updateGoogleContact(Contact contact) throws IOException {
        if (contact.getGoogleResourceId() == null || contact.getGoogleResourceId().isEmpty()) {
            throw new IllegalArgumentException("Missing Google Resource ID for update.");
        }

        Person existingPerson = getPeopleService().people()
                .get(contact.getGoogleResourceId())
                .setPersonFields("names,emailAddresses,phoneNumbers")
                .execute();

        Person updatedPerson = new Person();
        updatedPerson.setResourceName(contact.getGoogleResourceId());
        updatedPerson.setEtag(existingPerson.getEtag());

        updatedPerson.setNames(contact.getName() != null
                ? List.of(new Name().setGivenName(contact.getName()))
                : Collections.emptyList());

        updatedPerson.setEmailAddresses(contact.getEmail() != null
                ? List.of(new EmailAddress().setValue(contact.getEmail()))
                : Collections.emptyList());

        updatedPerson.setPhoneNumbers(contact.getPhoneNumber() != null
                ? List.of(new PhoneNumber().setValue(contact.getPhoneNumber()))
                : Collections.emptyList());

        getPeopleService().people()
                .updateContact(contact.getGoogleResourceId(), updatedPerson)
                .setUpdatePersonFields("names,emailAddresses,phoneNumbers")
                .execute();
    }

    // Delete a contact from Google Contacts
    public void deleteGoogleContact(String resourceName) throws IOException {
        if (resourceName == null || resourceName.isEmpty()) {
            throw new IllegalArgumentException("Google Resource ID is required for deletion.");
        }
        getPeopleService().people().deleteContact(resourceName).execute();
    }
}

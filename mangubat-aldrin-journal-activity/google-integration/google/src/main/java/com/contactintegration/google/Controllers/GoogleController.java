package com.contactintegration.google.Controllers;

import com.contactintegration.google.Models.Contact;
import com.contactintegration.google.Repositories.ContactRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
public class GoogleController {

    @Autowired
    private ContactRepository contactRepository;

    @GetMapping("/google/contacts")
    public String syncGoogleContactsToLocal(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            Model model) throws IOException {

        GoogleCredential credential = new GoogleCredential()
                .setAccessToken(authorizedClient.getAccessToken().getTokenValue());

        PeopleService peopleService = new PeopleService.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("ContactIntegration")
                .build();

        ListConnectionsResponse response = peopleService.people().connections()
                .list("people/me")
                .setPersonFields("names,emailAddresses,phoneNumbers")
                .execute();

        List<Person> connections = response.getConnections();
        if (connections != null) {
            for (Person googlePerson : connections) {
                String name = googlePerson.getNames() != null && !googlePerson.getNames().isEmpty()
                        ? googlePerson.getNames().get(0).getDisplayName() : null;
                String email = googlePerson.getEmailAddresses() != null && !googlePerson.getEmailAddresses().isEmpty()
                        ? googlePerson.getEmailAddresses().get(0).getValue() : null;
                String phone = googlePerson.getPhoneNumbers() != null && !googlePerson.getPhoneNumbers().isEmpty()
                        ? googlePerson.getPhoneNumbers().get(0).getValue() : null;

                Optional<Contact> existingContact = (email != null)
                        ? contactRepository.findByEmail(email)
                        : Optional.empty();

                if (existingContact.isEmpty()) {
                    Contact newContact = new Contact(name, email, phone);
                    contactRepository.save(newContact);
                } else {
                    Contact contact = existingContact.get();
                    if (name != null) contact.setName(name);
                    if (phone != null) contact.setPhoneNumber(phone);
                    contactRepository.save(contact);
                }
            }
        }

        return "redirect:/contacts";
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }
}
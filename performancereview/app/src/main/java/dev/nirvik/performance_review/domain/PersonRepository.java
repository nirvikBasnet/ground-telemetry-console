package dev.nirvik.performance_review;

import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class PersonRepository {
    MutableLiveData<List<Person>> persons = new MutableLiveData<>();

    public void loadPersons() {
        //TODO: load persons from database
    }


}
